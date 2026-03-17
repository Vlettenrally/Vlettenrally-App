package nl.vlettenrally.websockets;

import io.quarkus.logging.Log;
import io.quarkus.redis.datasource.ReactiveRedisDataSource;
import io.quarkus.redis.datasource.sortedset.ReactiveSortedSetCommands;
import io.quarkus.redis.datasource.sortedset.ScoreRange;
import io.smallrye.mutiny.Uni;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Tracks which admin WebSocket sessions are actively viewing the map.
 * <p>
 * State is stored in Valkey sorted sets so it is shared across pods and
 * automatically cleaned up when sessions expire.
 * <p>
 * Each game has a sorted set {@code map:subs:{gameId}} where the members
 * are session IDs and the score is the heartbeat epoch-second timestamp.
 * <p>
 * Admin clients send MAP_HEARTBEAT every 10 s while the map page is mounted.
 * Sessions that haven't sent a heartbeat in 30 s are considered stale.
 */
@ApplicationScoped
public class MapSubscriptionManager {

    private static final String KEY_PREFIX = "map:subs:";
    private static final Duration HEARTBEAT_TIMEOUT = Duration.ofSeconds(30);

    @Inject
    ReactiveRedisDataSource redisDataSource;

    /**
     * Register or refresh a map subscription for an admin session.
     * Called when MAP_HEARTBEAT is received.
     *
     * @param sessionId the WebSocket session id
     * @param gameId    the game the admin is currently viewing
     */
    public void subscribe(String sessionId, UUID gameId) {
        if (gameId == null) {
            Log.debugf("Ignoring map subscription for session %s: no game selected", sessionId);
            return;
        }

        String key = KEY_PREFIX + gameId;
        double score = Instant.now().getEpochSecond();

        ReactiveSortedSetCommands<String, String> zset = redisDataSource.sortedSet(String.class);
        zset.zadd(key, score, sessionId)
                .chain(() -> redisDataSource.key().expire(key, HEARTBEAT_TIMEOUT.multipliedBy(2)))
                .subscribe().with(
                        ok -> Log.debugf("Map subscription updated for session %s (game %s)", sessionId, gameId),
                        err -> Log.errorf("Failed to update map subscription in Valkey: %s", err.getMessage())
                );
    }

    /**
     * Remove a map subscription for an admin session from a specific game.
     * Called when the admin navigates away from the map or disconnects.
     */
    public void unsubscribe(String sessionId, UUID gameId) {
        if (gameId == null) return;

        String key = KEY_PREFIX + gameId;
        ReactiveSortedSetCommands<String, String> zset = redisDataSource.sortedSet(String.class);
        zset.zrem(key, sessionId)
                .subscribe().with(
                        removed -> Log.debugf("Map subscription removed for session %s (game %s)", sessionId, gameId),
                        err -> Log.errorf("Failed to remove map subscription from Valkey: %s", err.getMessage())
                );
    }

    /**
     * Get all active admin session IDs subscribed to map updates for a given game.
     * Prunes stale entries (score older than heartbeat timeout) in the same call.
     *
     * @return Set of session IDs (blocking – used from the scheduler thread)
     */
    public Set<String> getActiveSubscribers(UUID gameId) {
        String key = KEY_PREFIX + gameId;
        double cutoff = Instant.now().minus(HEARTBEAT_TIMEOUT).getEpochSecond();

        ReactiveSortedSetCommands<String, String> zset = redisDataSource.sortedSet(String.class);

        // Remove stale entries first, then fetch active ones
        return zset.zremrangebyscore(key, ScoreRange.from(Double.NEGATIVE_INFINITY, cutoff))
                .chain(() -> zset.zrangebyscore(key, ScoreRange.from(cutoff, Double.POSITIVE_INFINITY)))
                .onFailure().recoverWithItem(err -> {
                    Log.errorf("Failed to get map subscribers from Valkey: %s", err.getMessage());
                    return Collections.<String>emptyList();
                })
                .map(HashSet::new)
                .await().atMost(Duration.ofSeconds(5));
    }
}
