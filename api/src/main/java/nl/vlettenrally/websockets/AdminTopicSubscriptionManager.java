package nl.vlettenrally.websockets;

import io.quarkus.logging.Log;
import io.quarkus.redis.datasource.ReactiveRedisDataSource;
import io.quarkus.redis.datasource.sortedset.ReactiveSortedSetCommands;
import io.quarkus.redis.datasource.sortedset.ScoreRange;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import java.time.Duration;
import java.time.Instant;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/**
 * Tracks which admin WebSocket sessions are actively viewing a given admin
 * sub-page ("topic"), so occasional admin broadcasts are only streamed to the
 * sessions that can actually use them.
 * <p>
 * This generalises {@link MapSubscriptionManager} to an arbitrary topic. State
 * is stored in Valkey sorted sets so it is shared across pods and automatically
 * cleaned up when sessions expire.
 * <p>
 * Each (topic, game) pair has a sorted set {@code admin:subs:{topic}:{gameId}}
 * where the members are session IDs and the score is the heartbeat epoch-second
 * timestamp.
 * <p>
 * Admin clients send ADMIN_HEARTBEAT (carrying the topic) every 10 s while the
 * relevant page is mounted. Sessions that haven't sent a heartbeat in 30 s are
 * considered stale.
 */
@ApplicationScoped
public class AdminTopicSubscriptionManager {

    private static final String KEY_PREFIX = "admin:subs:";
    private static final Duration HEARTBEAT_TIMEOUT = Duration.ofSeconds(30);

    @Inject
    ReactiveRedisDataSource redisDataSource;

    private static String key(String topic, UUID gameId) {
        return KEY_PREFIX + topic + ":" + gameId;
    }

    /**
     * Register or refresh a topic subscription for an admin session.
     * Called when ADMIN_HEARTBEAT is received.
     *
     * @param sessionId the WebSocket session id
     * @param gameId    the game the admin is currently viewing
     * @param topic     the admin sub-page the admin is viewing
     */
    public void subscribe(String sessionId, UUID gameId, String topic) {
        if (gameId == null) {
            Log.debugf("Ignoring admin topic subscription for session %s: no game selected", sessionId);
            return;
        }
        if (topic == null || topic.isBlank()) {
            Log.debugf("Ignoring admin topic subscription for session %s: no topic", sessionId);
            return;
        }

        String key = key(topic, gameId);
        double score = Instant.now().getEpochSecond();

        ReactiveSortedSetCommands<String, String> zset = redisDataSource.sortedSet(String.class);
        zset.zadd(key, score, sessionId)
                .chain(() -> redisDataSource.key().expire(key, HEARTBEAT_TIMEOUT.multipliedBy(2)))
                .subscribe().with(
                        ok -> Log.debugf("Admin topic subscription updated for session %s (topic %s, game %s)",
                                sessionId, topic, gameId),
                        err -> Log.errorf("Failed to update admin topic subscription in Valkey: %s", err.getMessage())
                );
    }

    /**
     * Get all active admin session IDs subscribed to a topic for a given game.
     * Prunes stale entries (score older than heartbeat timeout) in the same call.
     *
     * @return Set of session IDs (blocking – call sites run on worker threads)
     */
    public Set<String> getActiveSubscribers(UUID gameId, String topic) {
        if (gameId == null || topic == null || topic.isBlank()) {
            return Collections.emptySet();
        }

        String key = key(topic, gameId);
        double cutoff = Instant.now().minus(HEARTBEAT_TIMEOUT).getEpochSecond();

        ReactiveSortedSetCommands<String, String> zset = redisDataSource.sortedSet(String.class);

        // Remove stale entries first, then fetch active ones
        return zset.zremrangebyscore(key, ScoreRange.from(Double.NEGATIVE_INFINITY, cutoff))
                .chain(() -> zset.zrangebyscore(key, ScoreRange.from(cutoff, Double.POSITIVE_INFINITY)))
                .onFailure().recoverWithItem(err -> {
                    Log.errorf("Failed to get admin topic subscribers from Valkey: %s", err.getMessage());
                    return Collections.<String>emptyList();
                })
                .map(HashSet<String>::new)
                .await().atMost(Duration.ofSeconds(5));
    }
}
