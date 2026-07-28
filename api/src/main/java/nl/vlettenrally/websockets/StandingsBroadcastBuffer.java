package nl.vlettenrally.websockets;

import io.quarkus.logging.Log;
import io.quarkus.scheduler.Scheduled;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import nl.vlettenrally.observability.WebSocketMetrics;
import nl.vlettenrally.observability.WebSocketMetrics.Buffer;
import nl.vlettenrally.observability.WebSocketMetrics.FlushOutcome;
import nl.vlettenrally.service.PointsLedgerService;

import java.util.ArrayList;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Coalesces the ranking / rival-view broadcasts driven by continuous distance accrual.
 * <p>
 * Distance points accrue every 100 m per team per location fix, so publishing standings on each
 * accrual fans out O(teams) work (a full team load + per-team rival broadcast) on nearly every
 * fix — the dominant worker-CPU cost during an active game. Instead, each accrual only records
 * the changed team here ({@link #markDirty}); a {@link Scheduled} flush then publishes the
 * affected views <em>once per interval per game</em> (see
 * {@code vlettenrally.points.standings-broadcast-interval}), independent of team count.
 * <p>
 * Mirrors {@link LocationBroadcastBuffer}: the discrete admin point paths still broadcast
 * immediately; only the high-frequency accrual path is buffered. The tradeoff is that players'
 * live ranking / rival totals update on the flush interval rather than instantly — the same
 * bound the admin map already lives with.
 */
@ApplicationScoped
public class StandingsBroadcastBuffer {

    /** gameId → the set of teamIds whose totals changed since the last flush. */
    private final ConcurrentHashMap<UUID, Set<UUID>> dirty = new ConcurrentHashMap<>();

    @Inject
    PointsLedgerService pointsLedgerService;

    @Inject
    WebSocketMetrics metrics;

    /**
     * Record that a team's total changed, to be reflected on the next flush.
     * Thread-safe; repeated calls for the same game coalesce into one broadcast.
     */
    public void markDirty(UUID gameId, UUID teamId) {
        dirty.computeIfAbsent(gameId, k -> ConcurrentHashMap.newKeySet()).add(teamId);
    }

    /**
     * Publish coalesced standings for every game with pending changes. Games with nothing
     * pending are skipped. A game's changed-team set is removed atomically before publishing,
     * so accruals arriving during the flush accumulate in a fresh set for the next tick.
     */
    @Scheduled(every = "{vlettenrally.points.standings-broadcast-interval}",
               concurrentExecution = Scheduled.ConcurrentExecution.SKIP)
    void flush() {
        if (dirty.isEmpty()) {
            return;
        }
        for (UUID gameId : new ArrayList<>(dirty.keySet())) {
            Set<UUID> changedTeams = dirty.remove(gameId);
            if (changedTeams == null || changedTeams.isEmpty()) {
                continue;
            }
            try {
                pointsLedgerService.publishStandingsForTeams(gameId, changedTeams);
                metrics.flushed(Buffer.STANDINGS, FlushOutcome.SENT);
            } catch (Exception e) {
                metrics.flushed(Buffer.STANDINGS, FlushOutcome.DROPPED);
                Log.errorf("Standings broadcast flush failed for game %s: %s", gameId, e.getMessage());
            }
        }
    }
}
