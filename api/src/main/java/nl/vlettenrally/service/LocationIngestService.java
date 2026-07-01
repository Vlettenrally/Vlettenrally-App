package nl.vlettenrally.service;

import io.quarkus.logging.Log;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import nl.vlettenrally.data.domain.Game;
import nl.vlettenrally.data.domain.LocationData;
import nl.vlettenrally.data.domain.Team;
import nl.vlettenrally.data.dto.game.TeamLocationResponse;
import nl.vlettenrally.data.repository.CheckpointRepository;
import nl.vlettenrally.data.repository.LocationDataRepository;
import nl.vlettenrally.data.repository.TeamRepository;
import nl.vlettenrally.service.util.TrackOutlierFilter;
import nl.vlettenrally.websockets.LocationBroadcastBuffer;
import nl.vlettenrally.websockets.WebSocketEventTypes;
import nl.vlettenrally.websockets.WebSocketPublisher;
import org.locationtech.jts.geom.Point;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Single choke point for persisting a team location fix, regardless of source
 * (Legendstracking polling or the Traccar/OsmAnd ingestion endpoint).
 * <p>
 * Applies abuse/quality guards, saves the fix, notifies the team's own devices,
 * and recomputes travelled distance. Admin-map broadcasting is delegated to
 * {@link nl.vlettenrally.websockets.LocationBroadcastBuffer} via {@link #broadcastToMap}
 * so that rapid per-device updates are coalesced before reaching the frontend.
 */
@ApplicationScoped
public class LocationIngestService {

    @Inject
    LocationDataRepository locationDataRepository;

    @Inject
    LocationBroadcastBuffer locationBroadcastBuffer;

    @Inject
    WebSocketPublisher webSocketPublisher;

    @Inject
    AutomationEngine automationEngine;

    @Inject
    TeamDistanceCalculator teamDistanceCalculator;

    @Inject
    GameService gameService;

    @Inject
    CheckpointRepository checkpointRepository;

    @Inject
    TeamRepository teamRepository;

    @Inject
    AchievementService achievementService;

    /** Resolves + caches the per-game outlier guards (falling back to global defaults). */
    @Inject
    LocationGuardConfig guardConfig;

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
        // The most recent accepted fix before this one; null on the very first fix. Also the
        // start of the segment used for checkpoint-pass detection below.
        LocationData previous = recent.isEmpty() ? null : recent.get(0);
        if (previous != null) {
            LocationData prev = recent.size() > 1 ? recent.get(1) : null;
            if (!passesGuards(prev, previous, reading)) {
                return Optional.empty();
            }
        }

        locationDataRepository.save(reading);

        TeamLocationResponse teamLocation = toResponse(reading, teamName);
        webSocketPublisher.sendToTeamOnly(
            reading.getGameId(),
            reading.getTeamId(),
            WebSocketEventTypes.LOCATION_UPDATE,
            teamLocation
        );

        // The game status drives both distance crediting and checkpoint detection;
        // look it up once here, the single choke point for every fix. A team that
        // has finished (reached its finish zone) is gated out the same way an
        // inactive game is — its meters advance the baseline but earn no points.
        //
        // Scoring (distance + speed) is ACTIVE-only. Checkpoint detection additionally runs
        // while a COMPLETED game is inside its post-end tail window, so a late finish-zone
        // crossing is still detected and its automation fires (e.g. a CHECKPOINT_ENTRY rule
        // comparing AFTER the game end). The rule itself still decides whether/how to act.
        Game game = gameService.findById(reading.getGameId()).orElse(null);
        boolean gameActive = game != null && game.getStatus() == Game.GameStatus.ACTIVE;
        boolean inTailWindow = game != null && game.getStatus() == Game.GameStatus.COMPLETED
            && game.isWithinPollingTailWindow(reading.getLocationTime());
        boolean teamFinished = teamRepository.findById(reading.getTeamId())
            .map(Team::isFinished)
            .orElse(false);
        boolean scoringActive = gameActive && !teamFinished;
        boolean detectCheckpoints = (gameActive || inTailWindow) && !teamFinished;

        double cumulativeMeters = teamDistanceCalculator.computeTraveledMeters(reading.getTeamId());
        automationEngine.onDistanceUpdate(reading.getGameId(), reading.getTeamId(), cumulativeMeters, scoringActive);

        if (scoringActive && reading.getSpeed() != null) {
            automationEngine.onSpeedExceeded(
                reading.getGameId(), reading.getTeamId(),
                reading.getSpeed().doubleValue());
        }
        if (detectCheckpoints) {
            detectCheckpointPasses(previous, reading);
        }

        return Optional.of(teamLocation);
    }

    /**
     * Credit every checkpoint each team is currently sitting inside, evaluated from each
     * team's latest fix. Called once when a game transitions to ACTIVE so a team already
     * parked in a start/checkpoint zone fires its automation immediately, rather than waiting
     * for the next poll (or being missed if it drifts out of the zone before that poll lands).
     * <p>
     * State-based on purpose: it uses point containment ({@link CheckpointRepository#findContainingPoint}),
     * not segment crossing, since there is no "previous" position to draw a segment from at kickoff.
     * Reuses the idempotent {@link AchievementService#recordVerifiedPass}, so a checkpoint already
     * credited (here or by a poll) neither re-credits points nor re-fires the automation. A team
     * whose latest fix is older than the staleness window is skipped — an ancient leftover fix is
     * not evidence the team is in the zone now.
     */
    @Transactional
    public void evaluateCurrentPositions(UUID gameId) {
        long staleFixSeconds = guardConfig.thresholdsFor(gameId).staleFixSeconds();
        Instant cutoff = Instant.now().minus(Duration.ofSeconds(staleFixSeconds));
        for (Team team : teamRepository.findByGameId(gameId)) {
            if (team.isFinished()) {
                continue;
            }
            LocationData latest = locationDataRepository.findLatestByTeamId(team.getId()).orElse(null);
            if (latest == null || latest.getLocationTime() == null
                    || latest.getLocationTime().isBefore(cutoff)) {
                continue;
            }
            for (UUID checkpointId : checkpointRepository.findContainingPoint(gameId, latest.getLocation())) {
                try {
                    achievementService.recordVerifiedPass(
                        team.getId(), checkpointId, latest.getLocationTime());
                } catch (Exception e) {
                    Log.warnf("Failed to record start-sweep pass team=%s checkpoint=%s: %s",
                        team.getId(), checkpointId, e.getMessage());
                }
            }
        }
    }

    /**
     * Auto-verify every checkpoint the boat passed since the previous fix. For two
     * consecutive fixes within the staleness window the straight segment between them is
     * tested ({@link CheckpointRepository#findCrossedBySegment}), so a zone transited between
     * fixes is credited even when neither fix lands inside it. On the first fix, or after a
     * gap longer than the staleness window (where the connecting line isn't a real path),
     * it falls back to the point test ({@link CheckpointRepository#findContainingPoint}).
     * A single spatial query resolves all matching checkpoints at once;
     * {@link AchievementService#recordVerifiedPass} is idempotent per (team, checkpoint).
     */
    private void detectCheckpointPasses(LocationData previous, LocationData reading) {
        long staleFixSeconds = guardConfig.thresholdsFor(reading.getGameId()).staleFixSeconds();
        boolean connect = previous != null
            && Duration.between(previous.getLocationTime(), reading.getLocationTime()).getSeconds() <= staleFixSeconds;
        List<UUID> matched = connect
            ? checkpointRepository.findCrossedBySegment(reading.getGameId(), previous.getLocation(), reading.getLocation())
            : checkpointRepository.findContainingPoint(reading.getGameId(), reading.getLocation());
        for (UUID checkpointId : matched) {
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
     * Queue a batch of accepted fixes for the next admin-map broadcast.
     * Actual delivery is handled by {@link LocationBroadcastBuffer} on its flush
     * interval, so rapid per-device updates are coalesced before hitting the frontend.
     */
    public void broadcastToMap(UUID gameId, List<TeamLocationResponse> locations) {
        locationBroadcastBuffer.buffer(gameId, locations);
    }

    private boolean isValid(LocationData reading) {
        Point p = reading.getLocation();
        if (p == null) {
            Log.infof("Dropping location for team %s: location point is null", reading.getTeamId());
            return false;
        }
        double lon = p.getX();
        double lat = p.getY();
        if (lat < -90 || lat > 90 || lon < -180 || lon > 180) {
            Log.infof("Dropping location for team %s: out-of-range lat/lon %f,%f",
                reading.getTeamId(), lat, lon);
            return false;
        }
        if (reading.getLocationTime() == null
                || reading.getLocationTime().isAfter(Instant.now().plusSeconds(MAX_FUTURE_SKEW_SECONDS))) {
            Log.infof("Dropping location for team %s: missing or future timestamp %s",
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
        TrackOutlierFilter.Thresholds thresholds = guardConfig.thresholdsFor(reading.getGameId());
        TrackOutlierFilter.Result result = TrackOutlierFilter.evaluate(prev, last, reading, thresholds);
        if (result.decision() != TrackOutlierFilter.Decision.ACCEPT) {
            Log.infof("Dropping location for team %s [%s]: %s",
                reading.getTeamId(), result.decision(), result.diagnostics());
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
