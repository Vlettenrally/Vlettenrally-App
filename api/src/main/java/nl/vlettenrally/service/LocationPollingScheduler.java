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
import nl.vlettenrally.data.repository.TeamRepository;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.Point;
import org.locationtech.jts.geom.PrecisionModel;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Polls the Legendstracking {@code /participants_locations} endpoint for every active
 * game with a base URL configured and broadcasts location updates. Checkpoint passes
 * are not taken from the external feed — they are detected internally from each fix via
 * {@link LocationIngestService} (PostGIS geofencing), the same path Traccar uses.
 * <p>
 * Runs at the configured interval on a single instance at a time — leader election
 * via {@link PollingLeader} (Valkey-backed lease).
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

    @Inject
    GameService gameService;

    @Inject
    TeamRepository teamRepository;

    @Inject
    LocationApiClient locationApiClient;

    @Inject
    LocationIngestService locationIngestService;

    @Inject
    PollingLeader pollingLeader;

    @Scheduled(every = "{vlettenrally.location.polling-interval}")
    @Transactional
    void pollLocations() {
        if (!pollingLeader.tryAcquireOrRenew()) {
            return;
        }

        List<Game> activeGames = gameService.findByStatus(Game.GameStatus.ACTIVE);

        for (Game game : activeGames) {
            if (!Boolean.TRUE.equals(game.getLocationEndpointActive())
                    || game.getLocationEndpointUrl() == null
                    || game.getLocationEndpointUrl().isBlank()) {
                Log.infof("Game %s: location polling skipped (endpoint not active or URL not set)", game.getId());
                continue;
            }

            try {
                pollGame(game);
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
            if (pl.getLocation() == null) continue;

            Team team = pl.getTrackerId() != null
                ? teamsByTrackerId.get(String.valueOf(pl.getTrackerId()))
                : null;
            if (team == null) continue;

            totalCount++;
            Point point = GEOMETRY_FACTORY.createPoint(
                new Coordinate(pl.getLocation().getLongitude(), pl.getLocation().getLatitude()));

            LocationData locationData = new LocationData();
            locationData.setTeamId(team.getId());
            locationData.setGameId(game.getId());
            locationData.setLocation(point);
            locationData.setLocationTime(parseInstant(pl.getLocationTime()));
            locationData.setAccuracy(pl.getGpsAccuracy() != null && pl.getGpsAccuracy() > 0
                ? BigDecimal.valueOf(pl.getGpsAccuracy()) : null);
            locationData.setSpeed(pl.getSpeed() != null ? BigDecimal.valueOf(pl.getSpeed()) : null);
            locationData.setHeading(null);

            locationIngestService.ingest(locationData, team.getName()).ifPresent(broadcasts::add);
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
