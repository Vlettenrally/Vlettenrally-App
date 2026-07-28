package nl.vlettenrally.websockets;

import io.quarkus.logging.Log;
import io.quarkus.scheduler.Scheduled;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import nl.vlettenrally.data.dto.game.TeamLocationResponse;
import nl.vlettenrally.observability.WebSocketMetrics;
import nl.vlettenrally.observability.WebSocketMetrics.Buffer;
import nl.vlettenrally.observability.WebSocketMetrics.FlushOutcome;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Buffers pending location updates and flushes them on a configurable interval (see
 * {@code vlettenrally.location.broadcast-interval}), as a single batched WebSocket message
 * for the admin map and a single multiplexed envelope for the teams' own devices.
 * <p>
 * Only the <em>latest</em> fix per team is retained between flushes, so rapid
 * per-device updates from the Traccar endpoint are automatically deduplicated.
 * The polling scheduler's sweep batches also pass through here, keeping a single
 * fan-out path for all sources.
 */
@ApplicationScoped
public class LocationBroadcastBuffer {

    /** gameId → (teamId → latest location), for the admin map. */
    private final ConcurrentHashMap<UUID, ConcurrentHashMap<UUID, TeamLocationResponse>> pending =
            new ConcurrentHashMap<>();

    /**
     * gameId → (teamId → latest location), for delivery back to each team's own devices.
     * Kept separate from {@link #pending} because the two have different sources: the map
     * batch is fed once per sweep, this one once per accepted fix.
     */
    private final ConcurrentHashMap<UUID, ConcurrentHashMap<UUID, TeamLocationResponse>> pendingTeamLocations =
            new ConcurrentHashMap<>();

    @Inject
    WebSocketMetrics metrics;

    @Inject
    AdminTopicSubscriptionManager adminTopicSubscriptionManager;

    @Inject
    WebSocketPublisher webSocketPublisher;

    /**
     * Add or replace the latest fix for each team in the given game.
     * Thread-safe; newer calls always win for the same teamId.
     */
    public void buffer(UUID gameId, List<TeamLocationResponse> locations) {
        if (locations.isEmpty()) {
            return;
        }
        ConcurrentHashMap<UUID, TeamLocationResponse> gameBuffer =
                pending.computeIfAbsent(gameId, k -> new ConcurrentHashMap<>());
        for (TeamLocationResponse loc : locations) {
            gameBuffer.put(loc.teamId(), loc);
        }
    }

    /**
     * Queue a team's own fix for delivery back to that team's devices.
     * <p>
     * Publishing this per fix meant one Valkey PUBLISH per team per sweep — a burst of
     * hundreds of un-awaited commands that overran the connection pool's wait queue. Buffering
     * collapses a whole sweep into one publish, at the cost of up to one interval of latency
     * before a team sees its own position move (the same bound the admin map already has).
     */
    public void bufferLocation(UUID gameId, TeamLocationResponse location) {
        pendingTeamLocations.computeIfAbsent(gameId, k -> new ConcurrentHashMap<>())
                .put(location.teamId(), location);
    }

    /**
     * Flush all pending locations to admin map subscribers. Runs on the configured
     * broadcast interval. Games with no pending fixes are skipped without touching
     * Valkey.
     */
    @Scheduled(every = "{vlettenrally.location.broadcast-interval}",
               concurrentExecution = Scheduled.ConcurrentExecution.SKIP)
    void flush() {
        flushTeamLocations();

        if (pending.isEmpty()) {
            return;
        }

        // Snapshot and clear atomically per game so new fixes arriving during flush
        // land in a fresh map rather than being lost.
        for (Map.Entry<UUID, ConcurrentHashMap<UUID, TeamLocationResponse>> entry : pending.entrySet()) {
            UUID gameId = entry.getKey();
            ConcurrentHashMap<UUID, TeamLocationResponse> gameBuffer = entry.getValue();

            // Swap out the game's buffer so concurrent writes go to a new map.
            if (!pending.remove(gameId, gameBuffer)) {
                // Another thread already replaced it; skip — next flush will catch it.
                continue;
            }
            if (gameBuffer.isEmpty()) {
                continue;
            }

            List<TeamLocationResponse> batch = new ArrayList<>(gameBuffer.values());
            Set<String> subscribers = adminTopicSubscriptionManager.getActiveSubscribers(gameId, AdminTopics.MAP);
            if (subscribers.isEmpty()) {
                Log.debug("Location broadcast flush: no active map subscribers for game " + gameId
                        + ", dropping " + batch.size() + " fix(es)");
                metrics.flushed(Buffer.LOCATION, FlushOutcome.DROPPED);
                continue;
            }

            Log.debug("Location broadcast flush: sending " + batch.size() + " fix(es) for game " + gameId
                    + " to " + subscribers.size() + " subscriber(s)");

            // One publish for the whole subscriber set; the receiving pod fans out locally.
            WebSocketMessage message = new WebSocketMessage(WebSocketEventTypes.LOCATION_UPDATE, batch)
                    .withTargetGame(gameId)
                    .forAdminOnly()
                    .withTargetSessions(subscribers);
            webSocketPublisher.publish(message);
            metrics.flushed(Buffer.LOCATION, FlushOutcome.SENT);
        }
    }

    /**
     * Flush each team's own latest fix as one multiplexed envelope per game, replacing what
     * used to be one publish per accepted fix.
     */
    private void flushTeamLocations() {
        if (pendingTeamLocations.isEmpty()) {
            return;
        }

        for (Map.Entry<UUID, ConcurrentHashMap<UUID, TeamLocationResponse>> entry : pendingTeamLocations.entrySet()) {
            UUID gameId = entry.getKey();
            ConcurrentHashMap<UUID, TeamLocationResponse> gameBuffer = entry.getValue();

            // Same swap-out as the map path: concurrent writes land in a fresh map.
            if (!pendingTeamLocations.remove(gameId, gameBuffer) || gameBuffer.isEmpty()) {
                continue;
            }

            Map<UUID, Object> perTeam = new HashMap<>(gameBuffer);
            Log.debug("Team location flush: sending " + perTeam.size() + " team fix(es) for game " + gameId);
            webSocketPublisher.publish(
                    WebSocketMessage.multiplexed(WebSocketEventTypes.LOCATION_UPDATE, gameId, perTeam));
        }
    }
}
