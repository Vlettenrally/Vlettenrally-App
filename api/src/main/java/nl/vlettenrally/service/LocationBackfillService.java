package nl.vlettenrally.service;

import io.quarkus.logging.Log;
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

    public enum Status { COMPLETED, COOLING_DOWN, BUSY, NO_PARTICIPANT, NOT_CONFIGURED }

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

    /**
     * Manual entry point: resolves the team's event-participant id from the live feed, then backfills.
     */
    public BackfillResult backfillTeamTrack(UUID gameId, UUID teamId) throws LocationApiException {
        return run(gameId, teamId, null);
    }

    /**
     * Automatic entry point: the poller already knows the participant id, so pass it to avoid a second
     * {@code /participants_locations} fetch.
     */
    public BackfillResult backfillTeamTrack(UUID gameId, UUID teamId, int eventParticipantId)
            throws LocationApiException {
        return run(gameId, teamId, eventParticipantId);
    }

    /**
     * Fire-and-forget dispatch used by the poller: runs the backfill on a worker thread so the heavy
     * provider call + recompute never block the poll loop, logging the outcome. Throttling makes this
     * safe to call on every poll tick that detects a gap.
     */
    public void triggerAutoBackfill(UUID gameId, UUID teamId, int eventParticipantId) {
        Infrastructure.getDefaultWorkerPool().execute(() -> {
            try {
                BackfillResult result = backfillTeamTrack(gameId, teamId, eventParticipantId);
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

    private BackfillResult run(UUID gameId, UUID teamId, Integer knownParticipantId) throws LocationApiException {
        // Lock first so a second trigger for the same team does not consume the cooldown window while
        // a backfill is already in flight; cooldown second so genuinely-throttled callers get 429.
        if (!throttle.tryAcquireLock(teamId)) {
            return BackfillResult.of(Status.BUSY);
        }
        try {
            if (!throttle.tryStartCooldown(teamId)) {
                return BackfillResult.coolingDown(throttle.cooldownRemainingSeconds(teamId));
            }
            return execute(gameId, teamId, knownParticipantId);
        } finally {
            throttle.releaseLock(teamId);
        }
    }

    private BackfillResult execute(UUID gameId, UUID teamId, Integer knownParticipantId)
            throws LocationApiException {
        Game game = gameService.findById(gameId).orElse(null);
        if (game == null || game.getLocationEndpointUrl() == null || game.getLocationEndpointUrl().isBlank()) {
            return BackfillResult.of(Status.NOT_CONFIGURED);
        }
        Team team = teamRepository.findById(teamId).orElse(null);
        if (team == null) {
            return BackfillResult.of(Status.NO_PARTICIPANT);
        }

        int participantId;
        if (knownParticipantId != null) {
            participantId = knownParticipantId;
        } else {
            Integer resolved = resolveParticipantId(game, team);
            if (resolved == null) {
                return BackfillResult.of(Status.NO_PARTICIPANT);
            }
            participantId = resolved;
        }

        // Heavy provider call — deliberately outside the DB transaction (which the persister owns).
        EventParticipantTrackResponse response =
            locationApiClient.fetchParticipantTrack(game.getLocationEndpointUrl(), participantId);
        List<EventParticipantLocation> points = response != null && response.getParticipantTrack() != null
            ? response.getParticipantTrack().getParticipantTrack() : null;
        if (points == null || points.isEmpty()) {
            return BackfillResult.completed(0, 0, 0);
        }

        return persister.persist(game, team, points);
    }

    /** Resolve the team's event-participant id via the live snapshot (matched by physical tracker id). */
    private Integer resolveParticipantId(Game game, Team team) throws LocationApiException {
        if (team.getTrackerId() == null || team.getTrackerId().isBlank()) {
            return null;
        }
        EventParticipantsLocationsResponse response =
            locationApiClient.fetchParticipantLocations(game.getLocationEndpointUrl());
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
