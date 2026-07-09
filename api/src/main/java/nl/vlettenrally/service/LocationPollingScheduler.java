package nl.vlettenrally.service;

import io.quarkus.logging.Log;
import io.quarkus.scheduler.Scheduled;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import nl.vlettenrally.client.LocationApiClient;
import nl.vlettenrally.client.dto.legendstracking.EventParticipantLocation;
import nl.vlettenrally.client.dto.legendstracking.EventParticipantsLocationsResponse;
import nl.vlettenrally.client.exception.LocationApiException;
import nl.vlettenrally.data.domain.Game;
import nl.vlettenrally.data.domain.LocationData;
import nl.vlettenrally.data.domain.Team;
import nl.vlettenrally.data.dto.game.TeamLocationResponse;
import nl.vlettenrally.data.repository.LocationDataRepository;
import nl.vlettenrally.data.repository.TeamRepository;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.Point;
import org.locationtech.jts.geom.PrecisionModel;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * Polls the Legendstracking {@code /participants_locations} endpoint for every active
 * game with a base URL configured and broadcasts location updates. Checkpoint passes
 * are not taken from the external feed — they are detected internally from each fix via
 * {@link LocationIngestService} (PostGIS geofencing), the same path Traccar uses.
 * <p>
 * The scheduler fires at {@code vlettenrally.location.polling-interval} (the tick rate /
 * minimum floor). Each game is only polled when at least {@code game.locationPollingFrequency}
 * seconds have elapsed since its previous poll, so different games can run at different
 * effective rates without running multiple scheduler threads.
 * <p>
 * Runs on a single instance at a time — leader election via {@link PollingLeader}
 * (Valkey-backed lease). Poll timestamps are tracked in memory: a leader change causes
 * at most one extra immediate poll per game, which is harmless.
 * <p>
 * Teams are matched to participants by Team.trackerId (the physical tracker ID
 * from the external provider). Participant tracker IDs without a matching team
 * are silently skipped.
 * <p>
 * NOTE: status_id, tracker_id, battery_percentage and is_sos_report from the
 * locations endpoint are not persisted yet — see plan "Out of scope" section.
 */
@ApplicationScoped
public class LocationPollingScheduler {

    private static final GeometryFactory GEOMETRY_FACTORY = new GeometryFactory(new PrecisionModel(), 4326);

    /** Last successful poll timestamp per game, used to enforce per-game polling frequency. */
    private final ConcurrentHashMap<UUID, Instant> lastPolledAt = new ConcurrentHashMap<>();

    @Inject
    GameService gameService;

    @Inject
    TeamRepository teamRepository;

    @Inject
    LocationApiClient locationApiClient;

    @Inject
    LocationIngestService locationIngestService;

    @Inject
    LocationDataRepository locationDataRepository;

    @Inject
    LocationBackfillService locationBackfillService;

    @Inject
    BackfillThrottle backfillThrottle;

    @Inject
    PollingLeader pollingLeader;

    /** A fresh fix this much newer than a team's last stored fix means the tracker was offline —
     *  automatically fetch its buffered history to fill the gap (throttled per team). */
    @ConfigProperty(name = "vlettenrally.location.backfill.gap-threshold", defaultValue = "PT15M")
    Duration backfillGapThreshold;

    @Scheduled(every = "{vlettenrally.location.polling-interval}")
    @Transactional
    void pollLocations() {
        if (!pollingLeader.tryAcquireOrRenew()) {
            return;
        }

        // Active games, plus PLANNED games whose pre-start lead window is open: polling a
        // game before kickoff seeds recent fixes so a team sitting in the start zone is
        // detected the instant the game goes ACTIVE (see GameStatusScheduler / the sweep).
        // Also COMPLETED games whose post-end tail window is still open, so a late finish-zone
        // crossing is detected and its automation fires (see LocationIngestService gate).
        List<Game> games = new ArrayList<>(gameService.findByStatus(Game.GameStatus.ACTIVE));
        Instant now = Instant.now();
        for (Game game : gameService.findByStatus(Game.GameStatus.PLANNED)) {
            if (game.isWithinPollingLeadWindow(now)) {
                games.add(game);
            }
        }
        for (Game game : gameService.findByStatus(Game.GameStatus.COMPLETED)) {
            if (game.isWithinPollingTailWindow(now)) {
                games.add(game);
            }
        }

        // Collect the IDs of games still in the candidate set so we can prune stale entries.
        Set<UUID> activeGameIds = new java.util.HashSet<>();
        for (Game game : games) {
            activeGameIds.add(game.getId());
        }
        lastPolledAt.keySet().retainAll(activeGameIds);

        for (Game game : games) {
            if (!Boolean.TRUE.equals(game.getLocationEndpointActive())
                    || game.getLocationEndpointUrl() == null
                    || game.getLocationEndpointUrl().isBlank()) {
                Log.debugf("Game %s: location polling skipped (endpoint not active or URL not set)", game.getId());
                continue;
            }

            // Respect the per-game polling frequency. The scheduler tick is a floor; each game
            // is only polled when at least locationPollingFrequency seconds have elapsed.
            int frequencySeconds = game.getLocationPollingFrequency() != null
                    ? game.getLocationPollingFrequency() : 30;
            Instant last = lastPolledAt.get(game.getId());
            if (last != null && Duration.between(last, now).getSeconds() < frequencySeconds) {
                Log.debugf("Game %s: skipping poll (%.0fs since last, frequency=%ds)",
                        game.getId(), (double) Duration.between(last, now).getSeconds(), frequencySeconds);
                continue;
            }

            try {
                pollGame(game);
                lastPolledAt.put(game.getId(), now);
            } catch (Exception e) {
                Log.errorf("Failed to poll Legendstracking for game %s: %s", game.getId(), e.getMessage());
            }
        }
    }

    private void pollGame(Game game) {
        Map<String, Team> teamsByTrackerId = teamRepository.findByGameId(game.getId()).stream()
            .filter(t -> t.getTrackerId() != null && !t.getTrackerId().isBlank())
            .collect(Collectors.toMap(Team::getTrackerId, t -> t, (a, b) -> a));

        if (teamsByTrackerId.isEmpty()) {
            Log.warnf("Game %s has no teams with trackerId set", game.getId());
            return;
        }

        pollParticipantLocations(game, teamsByTrackerId);
    }

    private void pollParticipantLocations(Game game, Map<String, Team> teamsByTrackerId) {
        EventParticipantsLocationsResponse response;
        try {
            response = locationApiClient.fetchParticipantLocations(game.getLocationEndpointUrl());
        } catch (LocationApiException e) {
            Log.warnf("Legendstracking /participants_locations error for game %s: %s", game.getId(), e.getMessage());
            return;
        }

        if (response == null || response.getEvent() == null
                || response.getEvent().getEventParticipantLocations() == null) {
            Log.warnf("Game %s: /participants_locations returned null or empty body", game.getId());
            return;
        }

        // Persist + notify each fix through the shared ingest path (guards, distance,
        // per-team WS), collecting the accepted ones for a single admin-map broadcast.
        List<TeamLocationResponse> broadcasts = new ArrayList<>();
        int totalCount = 0;
        for (EventParticipantLocation pl : response.getEvent().getEventParticipantLocations()) {
            if (pl.getLocation() == null) {
                Log.debugf("Game %s: skipping participant with null location (trackerId=%s)", game.getId(), pl.getTrackerId());
                continue;
            }

            Team team = pl.getTrackerId() != null
                ? teamsByTrackerId.get(String.valueOf(pl.getTrackerId()))
                : null;
            if (team == null) {
                Log.debugf("Game %s: skipping participant with unmatched trackerId=%s", game.getId(), pl.getTrackerId());
                continue;
            }

            // Skip a team whose backfill is in flight: the bulk recompute mutates the same distance
            // accumulator, so a concurrent live insert here would race it.
            if (backfillThrottle.isLocked(team.getId())) {
                Log.debugf("Game %s: skipping team %s (backfill in progress)", game.getId(), team.getId());
                continue;
            }

            // Capture the last stored fix time BEFORE ingesting, to detect an offline gap below.
            Instant lastStored = locationDataRepository.findLatestByTeamId(team.getId())
                .map(LocationData::getLocationTime).orElse(null);
            Instant fixTime = parseInstant(pl.getLocationTime());

            totalCount++;
            Point point = GEOMETRY_FACTORY.createPoint(
                new Coordinate(pl.getLocation().getLongitude(), pl.getLocation().getLatitude()));

            LocationData locationData = new LocationData();
            locationData.setTeamId(team.getId());
            locationData.setGameId(game.getId());
            locationData.setLocation(point);
            locationData.setLocationTime(fixTime);
            locationData.setAccuracy(pl.getGpsAccuracy() != null && pl.getGpsAccuracy() > 0
                ? BigDecimal.valueOf(pl.getGpsAccuracy()) : null);
            locationData.setSpeed(pl.getSpeed() != null ? BigDecimal.valueOf(pl.getSpeed()) : null);
            locationData.setHeading(null);

            locationIngestService.ingest(locationData, team.getName(), team.getDescription()).ifPresent(broadcasts::add);

            // Automatic gap backfill: a fresh fix far newer than the last stored one means the tracker
            // was offline and has buffered points we never polled. Fetch its history to fill the trail.
            // Fire-and-forget + throttled, so repeated ticks during the gap coalesce into one run.
            if (lastStored != null && fixTime != null
                    && Duration.between(lastStored, fixTime).compareTo(backfillGapThreshold) > 0) {
                locationBackfillService.triggerAutoBackfill(game.getId(), team.getId(), pl.getEventParticipantsId());
            }
        }

        Log.infof("Game %s: polled %d participant locations, accepted %d", game.getId(), totalCount, broadcasts.size());
        locationIngestService.broadcastToMap(game.getId(), broadcasts);
    }

    private static Instant parseInstant(String raw) {
        if (raw == null) return Instant.now();
        try {
            return Instant.parse(raw);
        } catch (Exception e) {
            return Instant.now();
        }
    }
}
