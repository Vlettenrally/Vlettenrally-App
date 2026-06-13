package nl.vlettenrally.service;

import io.quarkus.logging.Log;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import nl.vlettenrally.data.domain.Game;
import nl.vlettenrally.data.domain.LocationData;
import nl.vlettenrally.data.dto.game.TeamLocationResponse;
import nl.vlettenrally.data.repository.CheckpointRepository;
import nl.vlettenrally.data.repository.LocationDataRepository;
import nl.vlettenrally.service.util.TrackOutlierFilter;
import nl.vlettenrally.websockets.MapSubscriptionManager;
import nl.vlettenrally.websockets.WebSocketEventTypes;
import nl.vlettenrally.websockets.WebSocketMessage;
import nl.vlettenrally.websockets.WebSocketPublisher;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.locationtech.jts.geom.Point;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * Single choke point for persisting a team location fix, regardless of source
 * (Legendstracking polling or the Traccar/OsmAnd ingestion endpoint).
 * <p>
 * Applies abuse/quality guards, saves the fix, notifies the team's own devices,
 * and recomputes travelled distance. Admin-map broadcasting is left to the caller
 * via {@link #broadcastToMap} so the poller can batch a whole sweep into one
 * message while single-point sources broadcast one at a time.
 */
@ApplicationScoped
public class LocationIngestService {

    @Inject
    LocationDataRepository locationDataRepository;

    @Inject
    WebSocketPublisher webSocketPublisher;

    @Inject
    MapSubscriptionManager mapSubscriptionManager;

    @Inject
    AutomationEngine automationEngine;

    @Inject
    TeamDistanceCalculator teamDistanceCalculator;

    @Inject
    GameService gameService;

    @Inject
    CheckpointRepository checkpointRepository;

    @Inject
    AchievementService achievementService;

    /** Drop fixes that arrive faster than this since the team's last stored fix. */
    @ConfigProperty(name = "vlettenrally.location.min-interval-seconds", defaultValue = "5")
    long minIntervalSeconds;

    /** Hard teleport ceiling: drop fixes whose implied speed from the last fix exceeds this. */
    @ConfigProperty(name = "vlettenrally.location.max-speed-kmh", defaultValue = "120")
    double maxSpeedKmh;

    /** Soft ceiling for the direction-gated spike rule (boats move slowly). */
    @ConfigProperty(name = "vlettenrally.location.boat-max-speed-kmh", defaultValue = "25")
    double boatMaxSpeedKmh;

    /** A turn beyond this (degrees) is treated as a sharp reversal / spike candidate. */
    @ConfigProperty(name = "vlettenrally.location.turn-angle-threshold-degrees", defaultValue = "120")
    double turnAngleThresholdDegrees;

    /** Legs shorter than this (meters) give unreliable bearings, so skip the turn test. */
    @ConfigProperty(name = "vlettenrally.location.min-bearing-segment-meters", defaultValue = "10")
    double minBearingSegmentMeters;

    /** Drop fixes whose reported accuracy exceeds this (meters); null accuracy bypasses. */
    @ConfigProperty(name = "vlettenrally.location.max-accuracy-meters", defaultValue = "50")
    double maxAccuracyMeters;

    /** Gaps longer than this (seconds) bypass the boat-speed/turn rules so the track re-acquires. */
    @ConfigProperty(name = "vlettenrally.location.stale-fix-seconds", defaultValue = "300")
    long staleFixSeconds;

    /** Reject timestamps further than this into the future (clock skew / spoofing). */
    private static final long MAX_FUTURE_SKEW_SECONDS = 60;

    /**
     * Validate, guard, persist and notify a single location fix, then detect any
     * checkpoint passes internally via geofencing. All sources (the Legendstracking
     * poller and the Traccar/OsmAnd endpoint) flow through here and detect passes the
     * same way — from the fix itself, not from an external checkpoint feed.
     *
     * @return the broadcastable team location if accepted, or empty if the fix was
     *         rejected by a guard (caller should treat a rejection as a successful
     *         no-op — e.g. still return HTTP 200 to a tracker app).
     */
    @Transactional
    public Optional<TeamLocationResponse> ingest(LocationData reading, String teamName) {
        if (!isValid(reading)) {
            return Optional.empty();
        }

        // The outlier filter needs the last two fixes to derive the prior travel bearing.
        List<LocationData> recent = locationDataRepository.findRecentByTeamId(reading.getTeamId(), 2);
        if (!recent.isEmpty()) {
            LocationData last = recent.get(0);
            LocationData prev = recent.size() > 1 ? recent.get(1) : null;
            if (!passesGuards(prev, last, reading)) {
                return Optional.empty();
            }
        }

        locationDataRepository.save(reading);

        TeamLocationResponse teamLocation = toResponse(reading, teamName);
        webSocketPublisher.sendToTeam(
            reading.getGameId(),
            reading.getTeamId(),
            WebSocketEventTypes.LOCATION_UPDATE,
            teamLocation
        );

        // The game status drives both distance crediting and checkpoint detection;
        // look it up once here, the single choke point for every fix.
        boolean active = gameService.findById(reading.getGameId())
            .map(game -> game.getStatus() == Game.GameStatus.ACTIVE)
            .orElse(false);

        double cumulativeMeters = teamDistanceCalculator.computeTraveledMeters(reading.getTeamId());
        automationEngine.onDistanceUpdate(reading.getGameId(), reading.getTeamId(), cumulativeMeters, active);

        if (active) {
            detectCheckpointPasses(reading);
        }

        return Optional.of(teamLocation);
    }

    /**
     * Auto-verify every checkpoint whose geofence contains the fix. A single spatial
     * query ({@link CheckpointRepository#findContainingPoint}) resolves all matching
     * checkpoints at once. {@link AchievementService#recordVerifiedPass} is idempotent
     * per (team, checkpoint), so repeated fixes inside a zone don't re-award.
     */
    private void detectCheckpointPasses(LocationData reading) {
        for (UUID checkpointId : checkpointRepository.findContainingPoint(reading.getGameId(), reading.getLocation())) {
            try {
                achievementService.recordVerifiedPass(
                    reading.getTeamId(), checkpointId, reading.getLocationTime());
            } catch (Exception e) {
                Log.warnf("Failed to record geofenced pass team=%s checkpoint=%s: %s",
                    reading.getTeamId(), checkpointId, e.getMessage());
            }
        }
    }

    /**
     * Broadcast a batch of accepted fixes to admin clients watching the game map.
     * No-op for an empty batch.
     */
    public void broadcastToMap(UUID gameId, List<TeamLocationResponse> locations) {
        if (locations.isEmpty()) {
            return;
        }
        Set<String> subscribedSessionIds = mapSubscriptionManager.getActiveSubscribers(gameId);
        for (String sessionId : subscribedSessionIds) {
            WebSocketMessage message = new WebSocketMessage(WebSocketEventTypes.LOCATION_UPDATE, locations)
                .withTargetGame(gameId)
                .forAdminOnly()
                .withTargetSession(sessionId);
            webSocketPublisher.publish(message);
        }
    }

    private boolean isValid(LocationData reading) {
        Point p = reading.getLocation();
        if (p == null) {
            return false;
        }
        double lon = p.getX();
        double lat = p.getY();
        if (lat < -90 || lat > 90 || lon < -180 || lon > 180) {
            Log.debugf("Dropping location for team %s: out-of-range lat/lon %f,%f",
                reading.getTeamId(), lat, lon);
            return false;
        }
        if (reading.getLocationTime() == null
                || reading.getLocationTime().isAfter(Instant.now().plusSeconds(MAX_FUTURE_SKEW_SECONDS))) {
            Log.debugf("Dropping location for team %s: missing or future timestamp %s",
                reading.getTeamId(), reading.getLocationTime());
            return false;
        }
        return true;
    }

    /**
     * Online outlier guard. Combines time, distance (implied speed) and direction (turn angle)
     * to drop GPS spikes/teleports while preserving genuine sharp turns at boat speed.
     * Delegates the decision to the pure {@link TrackOutlierFilter}; {@code prev} may be null
     * when the team has only one prior fix.
     */
    private boolean passesGuards(LocationData prev, LocationData last, LocationData reading) {
        TrackOutlierFilter.Thresholds thresholds = new TrackOutlierFilter.Thresholds(
            minIntervalSeconds, maxAccuracyMeters, maxSpeedKmh, boatMaxSpeedKmh,
            turnAngleThresholdDegrees, minBearingSegmentMeters, staleFixSeconds);
        TrackOutlierFilter.Decision decision = TrackOutlierFilter.evaluate(prev, last, reading, thresholds);
        if (decision != TrackOutlierFilter.Decision.ACCEPT) {
            Log.debugf("Dropping location for team %s: %s", reading.getTeamId(), decision);
            return false;
        }
        return true;
    }

    private static TeamLocationResponse toResponse(LocationData reading, String teamName) {
        Point p = reading.getLocation();
        return new TeamLocationResponse(
            reading.getTeamId(),
            teamName,
            p.getY(),
            p.getX(),
            toDouble(reading.getSpeed()),
            toDouble(reading.getHeading()),
            reading.getLocationTime().toString()
        );
    }

    private static Double toDouble(BigDecimal value) {
        return value != null ? value.doubleValue() : null;
    }
}
