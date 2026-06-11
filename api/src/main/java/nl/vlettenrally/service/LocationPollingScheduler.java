package nl.vlettenrally.service;

import io.quarkus.logging.Log;
import io.quarkus.scheduler.Scheduled;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import nl.vlettenrally.client.LocationApiClient;
import nl.vlettenrally.client.dto.legendstracking.EventParticipantCheckpoint;
import nl.vlettenrally.client.dto.legendstracking.EventParticipantLocation;
import nl.vlettenrally.client.dto.legendstracking.EventParticipantsCheckpointsResponse;
import nl.vlettenrally.client.dto.legendstracking.EventParticipantsLocationsResponse;
import nl.vlettenrally.client.exception.LocationApiException;
import nl.vlettenrally.data.domain.Checkpoint;
import nl.vlettenrally.data.domain.Game;
import nl.vlettenrally.data.domain.LocationData;
import nl.vlettenrally.data.domain.Team;
import nl.vlettenrally.data.dto.game.TeamLocationResponse;
import nl.vlettenrally.data.repository.CheckpointRepository;
import nl.vlettenrally.data.repository.TeamRepository;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.Point;
import org.locationtech.jts.geom.PrecisionModel;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Polls Legendstracking endpoints for every active game with a base URL configured
 * and broadcasts location updates plus auto-verified checkpoint passes.
 * <p>
 * Runs at the configured interval on a single instance at a time — leader election
 * via {@link PollingLeader} (Valkey-backed lease).
 * <p>
 * Teams are matched to participants by Team.externalParticipantId, which is
 * populated during admin-triggered link-participants by joining bib == name.
 * Participant ids without a matching team are silently skipped.
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
    CheckpointRepository checkpointRepository;

    @Inject
    LocationApiClient locationApiClient;

    @Inject
    LocationIngestService locationIngestService;

    @Inject
    AchievementService achievementService;

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
        Map<Integer, Team> teamsByParticipantId = teamRepository.findByGameId(game.getId()).stream()
            .filter(t -> t.getExternalParticipantId() != null)
            .collect(Collectors.toMap(Team::getExternalParticipantId, t -> t, (a, b) -> a));

        if (teamsByParticipantId.isEmpty()) {
            Log.debugf("Game %s has no teams with externalParticipantId — run link-participants first", game.getId());
            return;
        }

        pollParticipantLocations(game, teamsByParticipantId);
        pollParticipantCheckpoints(game, teamsByParticipantId);
    }

    private void pollParticipantLocations(Game game, Map<Integer, Team> teamsByParticipantId) {
        EventParticipantsLocationsResponse response;
        try {
            response = locationApiClient.fetchParticipantLocations(game.getLocationEndpointUrl());
        } catch (LocationApiException e) {
            Log.warnf("Legendstracking /participants_locations error for game %s: %s", game.getId(), e.getMessage());
            return;
        }

        if (response == null || response.getEvent() == null
                || response.getEvent().getEventParticipantLocations() == null) {
            return;
        }

        // Persist + notify each fix through the shared ingest path (guards, distance,
        // per-team WS), collecting the accepted ones for a single admin-map broadcast.
        List<TeamLocationResponse> broadcasts = new ArrayList<>();
        for (EventParticipantLocation pl : response.getEvent().getEventParticipantLocations()) {
            if (pl.getLocation() == null) continue;

            Team team = teamsByParticipantId.get(pl.getEventParticipantsId());
            if (team == null) continue;

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

        locationIngestService.broadcastToMap(game.getId(), broadcasts);
    }

    private void pollParticipantCheckpoints(Game game, Map<Integer, Team> teamsByParticipantId) {
        EventParticipantsCheckpointsResponse response;
        try {
            response = locationApiClient.fetchParticipantCheckpoints(game.getLocationEndpointUrl());
        } catch (LocationApiException e) {
            Log.warnf("Legendstracking /participants_checkpoints error for game %s: %s", game.getId(), e.getMessage());
            return;
        }

        if (response == null || response.getEvent() == null
                || response.getEvent().getEventParticipantCheckpoints() == null) {
            return;
        }

        Map<Integer, Checkpoint> checkpointsByExternalId = new HashMap<>();

        for (EventParticipantCheckpoint epc : response.getEvent().getEventParticipantCheckpoints()) {
            Team team = teamsByParticipantId.get(epc.getEventParticipantsId());
            if (team == null) continue;

            Checkpoint checkpoint = checkpointsByExternalId.computeIfAbsent(epc.getCheckpointId(), id ->
                checkpointRepository.findByGameIdAndExternalId(game.getId(), String.valueOf(id)).orElse(null));
            if (checkpoint == null) continue;

            Instant passTime = parseInstant(epc.getLocationTime());
            try {
                achievementService.recordVerifiedPass(team.getId(), checkpoint.getId(), passTime);
            } catch (Exception e) {
                Log.warnf("Failed to record verified pass team=%s checkpoint=%s: %s",
                    team.getId(), checkpoint.getId(), e.getMessage());
            }
        }
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
