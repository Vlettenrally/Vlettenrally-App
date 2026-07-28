package nl.vlettenrally.service;

import io.quarkus.logging.Log;
import io.quarkus.narayana.jta.QuarkusTransaction;
import io.smallrye.mutiny.infrastructure.Infrastructure;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import nl.vlettenrally.client.LocationApiClient;
import nl.vlettenrally.client.dto.legendstracking.EventParticipantLocation;
import nl.vlettenrally.client.dto.legendstracking.EventParticipantTrackResponse;
import nl.vlettenrally.client.dto.legendstracking.EventParticipantsLocationsResponse;
import nl.vlettenrally.client.exception.LocationApiException;
import nl.vlettenrally.data.domain.Game;
import nl.vlettenrally.data.domain.Team;
import nl.vlettenrally.data.repository.TeamRepository;
import nl.vlettenrally.observability.LocationMetrics;
import nl.vlettenrally.observability.LocationMetrics.BackfillStatus;
import nl.vlettenrally.observability.LocationMetrics.BackfillTrigger;

import java.util.List;
import java.util.UUID;

/**
 * Backfills a team's location track from the Legendstracking per-participant history endpoint
 * ({@code /api/event/{eventParticipantId}/participant_track}) to fill gaps left when a tracker was
 * offline (the live poller only ever sees the latest fix). Two triggers use this service: a manual
 * admin button (per team) and the poller's automatic gap detection.
 * <p>
 * The provider endpoint is expensive, so every run is guarded by a per-team Valkey cooldown and a
 * per-team lock ({@link BackfillThrottle}); the live poller skips a team while its lock is held.
 * <p>
 * This service owns the throttling and the (non-transactional) provider HTTP calls; the atomic
 * database write + re-derivation is delegated to {@link LocationBackfillPersister} so the HTTP call
 * stays outside the transaction. Unlike the live single-fix path ({@link LocationIngestService#ingest}),
 * this is a rare bulk operation that inserts historical points in chronological order and re-derives
 * distance and checkpoint passes correctly for out-of-order data.
 */
@ApplicationScoped
public class LocationBackfillService {

    @Inject
    LocationApiClient locationApiClient;

    @Inject
    LocationBackfillPersister persister;

    @Inject
    GameService gameService;

    @Inject
    TeamRepository teamRepository;

    @Inject
    BackfillThrottle throttle;

    @Inject
    LocationMetrics metrics;

    public enum Status { COMPLETED, COOLING_DOWN, BUSY, NO_PARTICIPANT, NOT_CONFIGURED }

    private record GameAndTeam(Game game, Team team) {}

    /**
     * Outcome of a backfill request. {@code cooldownRemainingSeconds} is only meaningful for
     * {@link Status#COOLING_DOWN}.
     */
    public record BackfillResult(Status status, int inserted, int droppedByGuard,
                                 int checkpointPassesDetected, long cooldownRemainingSeconds) {
        static BackfillResult completed(int inserted, int dropped, int passes) {
            return new BackfillResult(Status.COMPLETED, inserted, dropped, passes, 0);
        }
        static BackfillResult coolingDown(long remaining) {
            return new BackfillResult(Status.COOLING_DOWN, 0, 0, 0, remaining);
        }
        static BackfillResult of(Status status) {
            return new BackfillResult(status, 0, 0, 0, 0);
        }
    }

    // Two entry points, one per trigger. Both funnel into run(), which is the only place a
    // backfill attempt is counted; the layers below it each add exactly one concern —
    // run (measure) → acquireAndExecute (throttle) → fetchAndPersist (work).

    /**
     * Manual entry point, called from the admin REST endpoint. The caller has no participant id,
     * so it is resolved from the live snapshot on the way through.
     */
    public BackfillResult backfillTeamTrack(UUID gameId, UUID teamId) throws LocationApiException {
        return run(gameId, teamId, null, BackfillTrigger.MANUAL);
    }

    /**
     * Automatic entry point, called by the poller when it detects a gap. Fire-and-forget on a worker
     * thread so the heavy provider call + recompute never block the poll loop; the poller already
     * knows the participant id, which avoids a second {@code /participants_locations} fetch.
     * Throttling makes this safe to call on every tick that detects a gap.
     */
    public void triggerAutoBackfill(UUID gameId, UUID teamId, int eventParticipantId) {
        Infrastructure.getDefaultWorkerPool().execute(() -> {
            try {
                BackfillResult result = run(gameId, teamId, eventParticipantId, BackfillTrigger.AUTO);
                if (result.status() == Status.COMPLETED && result.inserted() > 0) {
                    Log.infof("Auto-backfill team %s: inserted=%d dropped=%d checkpointPasses=%d",
                        teamId, result.inserted(), result.droppedByGuard(), result.checkpointPassesDetected());
                } else {
                    Log.debugf("Auto-backfill team %s: %s", teamId, result.status());
                }
            } catch (LocationApiException e) {
                Log.warnf("Auto-backfill team %s failed: %s", teamId, e.getMessage());
            } catch (Exception e) {
                Log.errorf("Auto-backfill team %s errored: %s", teamId, e.getMessage());
            }
        });
    }

    /**
     * Counts the attempt. Wrapping rather than recording at each return keeps
     * {@code location.backfill.run} honest: every attempt lands on it exactly once, whichever of the
     * five statuses — or an exception — it ended on.
     */
    private BackfillResult run(UUID gameId, UUID teamId, Integer knownParticipantId, BackfillTrigger trigger)
            throws LocationApiException {
        BackfillResult result;
        try {
            result = acquireAndExecute(gameId, teamId, knownParticipantId, trigger);
        } catch (LocationApiException | RuntimeException e) {
            metrics.backfillRun(trigger, BackfillStatus.ERROR);
            throw e;
        }
        metrics.backfillRun(trigger, LocationMetrics.backfillStatusOf(result.status()));
        return result;
    }

    /**
     * Applies the per-team lock and cooldown, and guarantees the lock is released. The trigger is
     * only carried through to attribute the provider calls below to the right caller.
     */
    private BackfillResult acquireAndExecute(UUID gameId, UUID teamId, Integer knownParticipantId,
            BackfillTrigger trigger) throws LocationApiException {
        // Lock first so a second trigger for the same team does not consume the cooldown window while
        // a backfill is already in flight; cooldown second so genuinely-throttled callers get 429.
        if (!throttle.tryAcquireLock(teamId)) {
            return BackfillResult.of(Status.BUSY);
        }
        try {
            if (!throttle.tryStartCooldown(teamId)) {
                return BackfillResult.coolingDown(throttle.cooldownRemainingSeconds(teamId));
            }
            return fetchAndPersist(gameId, teamId, knownParticipantId, trigger);
        } finally {
            throttle.releaseLock(teamId);
        }
    }

    /** Loads the game and team, resolves the participant, fetches the track and hands it to the persister. */
    private BackfillResult fetchAndPersist(UUID gameId, UUID teamId, Integer knownParticipantId,
            BackfillTrigger trigger) throws LocationApiException {
        // triggerAutoBackfill dispatches onto a plain worker-pool thread with no CDI request context
        // or JTA transaction, so the game/team reads need their own short transaction to get a usable
        // EntityManager; the manual entry point (backfillTeamTrack) runs on the request thread instead,
        // where the ambient request context already covers these same reads.
        GameAndTeam gameAndTeam = QuarkusTransaction.requiringNew()
            .call(() -> new GameAndTeam(gameService.findById(gameId).orElse(null), teamRepository.findById(teamId).orElse(null)));
        Game game = gameAndTeam.game();
        if (game == null || game.getLocationEndpointUrl() == null || game.getLocationEndpointUrl().isBlank()) {
            return BackfillResult.of(Status.NOT_CONFIGURED);
        }
        Team team = gameAndTeam.team();
        if (team == null) {
            return BackfillResult.of(Status.NO_PARTICIPANT);
        }

        int participantId;
        if (knownParticipantId != null) {
            participantId = knownParticipantId;
        } else {
            Integer resolved = resolveParticipantId(game, team, trigger);
            if (resolved == null) {
                return BackfillResult.of(Status.NO_PARTICIPANT);
            }
            participantId = resolved;
        }

        // Heavy provider call — deliberately outside the DB transaction (which the persister owns).
        EventParticipantTrackResponse response =
            locationApiClient.fetchParticipantTrack(game.getLocationEndpointUrl(), participantId,
                LocationMetrics.apiCallerOf(trigger));
        List<EventParticipantLocation> points = response != null && response.getParticipantTrack() != null
            ? response.getParticipantTrack().getParticipantTrack() : null;
        if (points == null || points.isEmpty()) {
            return BackfillResult.completed(0, 0, 0);
        }

        return persister.persist(game, team, points);
    }

    /**
     * Resolve the team's event-participant id via the live snapshot (matched by physical tracker id).
     * <p>
     * Only the manual trigger can reach this — the auto path always arrives with a known participant
     * id — but the caller attribution is still derived from the trigger rather than hardcoded, so
     * that assumption never becomes load-bearing.
     */
    private Integer resolveParticipantId(Game game, Team team, BackfillTrigger trigger)
            throws LocationApiException {
        if (team.getTrackerId() == null || team.getTrackerId().isBlank()) {
            return null;
        }
        EventParticipantsLocationsResponse response =
            locationApiClient.fetchParticipantLocations(game.getLocationEndpointUrl(),
                LocationMetrics.apiCallerOf(trigger));
        if (response == null || response.getEvent() == null
                || response.getEvent().getEventParticipantLocations() == null) {
            return null;
        }
        for (EventParticipantLocation pl : response.getEvent().getEventParticipantLocations()) {
            if (pl.getTrackerId() != null && team.getTrackerId().equals(String.valueOf(pl.getTrackerId()))) {
                return pl.getEventParticipantsId();
            }
        }
        return null;
    }
}
