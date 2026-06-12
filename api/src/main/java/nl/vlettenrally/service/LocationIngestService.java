package nl.vlettenrally.service;

import io.quarkus.logging.Log;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import nl.vlettenrally.data.domain.Checkpoint;
import nl.vlettenrally.data.domain.Game;
import nl.vlettenrally.data.domain.LocationData;
import nl.vlettenrally.data.dto.game.TeamLocationResponse;
import nl.vlettenrally.data.repository.CheckpointRepository;
import nl.vlettenrally.data.repository.LocationDataRepository;
import nl.vlettenrally.websockets.MapSubscriptionManager;
import nl.vlettenrally.websockets.WebSocketEventTypes;
import nl.vlettenrally.websockets.WebSocketMessage;
import nl.vlettenrally.websockets.WebSocketPublisher;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.locationtech.jts.geom.Point;

import java.math.BigDecimal;
import java.time.Duration;
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
    GeofencingService geofencingService;

    @Inject
    CheckpointRepository checkpointRepository;

    @Inject
    AchievementService achievementService;

    /** Drop fixes that arrive faster than this since the team's last stored fix. */
    @ConfigProperty(name = "vlettenrally.location.min-interval-seconds", defaultValue = "5")
    long minIntervalSeconds;

    /** Drop fixes whose implied speed from the last fix exceeds this (GPS teleports). */
    @ConfigProperty(name = "vlettenrally.location.max-speed-kmh", defaultValue = "120")
    double maxSpeedKmh;

    /** Reject timestamps further than this into the future (clock skew / spoofing). */
    private static final long MAX_FUTURE_SKEW_SECONDS = 60;
    private static final double EARTH_RADIUS_METERS = 6_371_000.0;

    /**
     * Validate, guard, persist and notify a single location fix, without local
     * checkpoint detection. Used by the Legendstracking poller, which gets
     * checkpoint passes from the external {@code /participants_checkpoints} feed.
     *
     * @return the broadcastable team location if accepted, or empty if the fix was
     *         rejected by a guard (caller should treat a rejection as a successful
     *         no-op — e.g. still return HTTP 200 to a tracker app).
     */
    @Transactional
    public Optional<TeamLocationResponse> ingest(LocationData reading, String teamName) {
        return ingest(reading, teamName, false);
    }

    /**
     * Validate, guard, persist and notify a single location fix.
     *
     * @param detectCheckpoints derive checkpoint passes from this fix via local
     *        geofencing. Raw-GPS sources (Traccar/OsmAnd) have no external
     *        checkpoint feed, so this is the only way they detect passes; the
     *        Legendstracking poller leaves it off and uses its feed instead.
     * @return the broadcastable team location if accepted, or empty if the fix was
     *         rejected by a guard.
     */
    @Transactional
    public Optional<TeamLocationResponse> ingest(LocationData reading, String teamName, boolean detectCheckpoints) {
        if (!isValid(reading)) {
            return Optional.empty();
        }

        Optional<LocationData> lastOpt = locationDataRepository.findLatestByTeamId(reading.getTeamId());
        if (lastOpt.isPresent() && !passesGuards(lastOpt.get(), reading)) {
            return Optional.empty();
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

        if (detectCheckpoints && active) {
            detectCheckpointPasses(reading);
        }

        return Optional.of(teamLocation);
    }

    /**
     * Auto-verify any checkpoint whose geofence now contains the fix, mirroring
     * the Legendstracking checkpoint feed for sources that only push raw positions.
     * {@link AchievementService#recordVerifiedPass} is idempotent per
     * (team, checkpoint), so repeated fixes inside a zone don't re-award.
     */
    private void detectCheckpointPasses(LocationData reading) {
        for (Checkpoint checkpoint : checkpointRepository.findByGameId(reading.getGameId())) {
            if (!geofencingService.isWithinCheckpointGeofence(reading, checkpoint)) {
                continue;
            }
            try {
                achievementService.recordVerifiedPass(
                    reading.getTeamId(), checkpoint.getId(), reading.getLocationTime());
            } catch (Exception e) {
                Log.warnf("Failed to record geofenced pass team=%s checkpoint=%s: %s",
                    reading.getTeamId(), checkpoint.getId(), e.getMessage());
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

    private boolean passesGuards(LocationData last, LocationData reading) {
        long deltaSeconds = Duration.between(last.getLocationTime(), reading.getLocationTime()).getSeconds();
        // Not newer than the last fix, or arriving too fast: throttle.
        if (deltaSeconds < minIntervalSeconds) {
            Log.debugf("Dropping location for team %s: only %ds since last fix (min %ds)",
                reading.getTeamId(), deltaSeconds, minIntervalSeconds);
            return false;
        }
        double meters = haversineMeters(last.getLocation(), reading.getLocation());
        double impliedKmh = (meters / deltaSeconds) * 3.6;
        if (impliedKmh > maxSpeedKmh) {
            Log.debugf("Dropping location for team %s: implied %.0f km/h over %.0f m (max %.0f)",
                reading.getTeamId(), impliedKmh, meters, maxSpeedKmh);
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

    private static double haversineMeters(Point a, Point b) {
        double lat1 = Math.toRadians(a.getY());
        double lat2 = Math.toRadians(b.getY());
        double dLat = lat2 - lat1;
        double dLon = Math.toRadians(b.getX() - a.getX());
        double h = Math.sin(dLat / 2) * Math.sin(dLat / 2)
            + Math.cos(lat1) * Math.cos(lat2) * Math.sin(dLon / 2) * Math.sin(dLon / 2);
        return 2 * EARTH_RADIUS_METERS * Math.asin(Math.min(1.0, Math.sqrt(h)));
    }
}
