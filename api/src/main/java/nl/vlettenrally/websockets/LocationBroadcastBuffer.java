package nl.vlettenrally.websockets;

import io.quarkus.logging.Log;
import io.quarkus.scheduler.Scheduled;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import nl.vlettenrally.data.dto.game.TeamLocationResponse;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Buffers pending admin-map location updates and flushes them as a single batched
 * WebSocket message on a configurable interval (see
 * {@code vlettenrally.location.broadcast-interval}).
 * <p>
 * Only the <em>latest</em> fix per team is retained between flushes, so rapid
 * per-device updates from the Traccar endpoint are automatically deduplicated.
 * The polling scheduler's sweep batches also pass through here, keeping a single
 * fan-out path for all sources.
 */
@ApplicationScoped
public class LocationBroadcastBuffer {

    /** gameId → (teamId → latest location) */
    private final ConcurrentHashMap<UUID, ConcurrentHashMap<UUID, TeamLocationResponse>> pending =
            new ConcurrentHashMap<>();

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
     * Flush all pending locations to admin map subscribers. Runs on the configured
     * broadcast interval. Games with no pending fixes are skipped without touching
     * Valkey.
     */
    @Scheduled(every = "{vlettenrally.location.broadcast-interval}")
    void flush() {
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
                continue;
            }

            Log.debug("Location broadcast flush: sending " + batch.size() + " fix(es) for game " + gameId
                    + " to " + subscribers.size() + " subscriber(s)");

            for (String sessionId : subscribers) {
                WebSocketMessage message = new WebSocketMessage(WebSocketEventTypes.LOCATION_UPDATE, batch)
                        .withTargetGame(gameId)
                        .forAdminOnly()
                        .withTargetSession(sessionId);
                webSocketPublisher.publish(message);
            }
        }
    }
}
