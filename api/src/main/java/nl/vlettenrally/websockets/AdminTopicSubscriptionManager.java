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
 * surface ("topic"), so per-page broadcasts are only streamed to the sessions
 * that can actually use them.
 * <p>
 * This is the single heartbeat-gating mechanism for admin surfaces: the live map
 * ({@link AdminTopics#MAP}), the checkpoint station ({@link AdminTopics#CHECKPOINT})
 * and the occasional per-page event streams (pictures, points, …). State is
 * stored in Valkey sorted sets so it is shared across pods and automatically
 * cleaned up when sessions expire.
 * <p>
 * Each (topic, scope) pair has a sorted set {@code admin:subs:{topic}:{scopeId}}
 * where the members are session IDs and the score is the heartbeat epoch-second
 * timestamp. The scope is the game id for game-wide topics and the checkpoint id
 * for {@link AdminTopics#CHECKPOINT}.
 * <p>
 * Admin clients send a heartbeat (ADMIN_HEARTBEAT carrying the topic, or
 * CHECKPOINT_HEARTBEAT) every 10 s while the relevant page is mounted. Sessions
 * that haven't sent a heartbeat in 30 s are considered stale.
 */
@ApplicationScoped
public class AdminTopicSubscriptionManager {

    private static final String KEY_PREFIX = "admin:subs:";
    private static final Duration HEARTBEAT_TIMEOUT = Duration.ofSeconds(30);

    @Inject
    ReactiveRedisDataSource redisDataSource;

    private static String key(String topic, UUID scopeId) {
        return KEY_PREFIX + topic + ":" + scopeId;
    }

    /**
     * Register or refresh a topic subscription for an admin session.
     * Called when a heartbeat is received.
     *
     * @param sessionId the WebSocket session id
     * @param scopeId   the game id (or checkpoint id for {@link AdminTopics#CHECKPOINT})
     * @param topic     the admin surface the session is viewing
     */
    public void subscribe(String sessionId, UUID scopeId, String topic) {
        if (scopeId == null) {
            Log.debugf("Ignoring admin topic subscription for session %s: no scope", sessionId);
            return;
        }
        if (topic == null || topic.isBlank()) {
            Log.debugf("Ignoring admin topic subscription for session %s: no topic", sessionId);
            return;
        }

        String key = key(topic, scopeId);
        double score = Instant.now().getEpochSecond();

        ReactiveSortedSetCommands<String, String> zset = redisDataSource.sortedSet(String.class);
        zset.zadd(key, score, sessionId)
                .chain(() -> redisDataSource.key().expire(key, HEARTBEAT_TIMEOUT.multipliedBy(2)))
                .subscribe().with(
                        ok -> Log.debugf("Admin topic subscription updated for session %s (topic %s, scope %s)",
                                sessionId, topic, scopeId),
                        err -> Log.errorf("Failed to update admin topic subscription in Valkey: %s", err.getMessage())
                );
    }

    /**
     * Remove a single topic subscription for an admin session. Called eagerly when
     * the session navigates away / switches game / disconnects, so high-frequency
     * topics (e.g. the map) stop streaming immediately rather than after the TTL.
     */
    public void unsubscribe(String sessionId, UUID scopeId, String topic) {
        if (scopeId == null || topic == null || topic.isBlank()) {
            return;
        }
        String key = key(topic, scopeId);
        ReactiveSortedSetCommands<String, String> zset = redisDataSource.sortedSet(String.class);
        zset.zrem(key, sessionId)
                .subscribe().with(
                        removed -> Log.debugf("Admin topic subscription removed for session %s (topic %s, scope %s)",
                                sessionId, topic, scopeId),
                        err -> Log.errorf("Failed to remove admin topic subscription from Valkey: %s", err.getMessage())
                );
    }

    /**
     * Remove this session from every game-scoped topic for the given game. Used on
     * game switch and disconnect so no stale game's stream lingers.
     */
    public void unsubscribeGameTopics(String sessionId, UUID gameId) {
        if (gameId == null) return;
        for (String topic : AdminTopics.GAME_SCOPED) {
            unsubscribe(sessionId, gameId, topic);
        }
    }

    /**
     * Get all active admin session IDs subscribed to a topic for a given scope.
     * Prunes stale entries (score older than heartbeat timeout) in the same call.
     *
     * @return Set of session IDs (blocking – call sites run on worker threads)
     */
    public Set<String> getActiveSubscribers(UUID scopeId, String topic) {
        if (scopeId == null || topic == null || topic.isBlank()) {
            return Collections.emptySet();
        }

        String key = key(topic, scopeId);
        double cutoff = Instant.now().minus(HEARTBEAT_TIMEOUT).getEpochSecond();

        ReactiveSortedSetCommands<String, String> zset = redisDataSource.sortedSet(String.class);

        // Remove stale entries first, then fetch active ones
        return zset.zremrangebyscore(key, ScoreRange.from(Double.NEGATIVE_INFINITY, cutoff))
                .chain(() -> zset.zrangebyscore(key, ScoreRange.from(cutoff, Double.POSITIVE_INFINITY)))
                .onFailure().recoverWithItem(err -> {
                    Log.errorf("Failed to get admin topic subscribers from Valkey: %s", err.getMessage());
                    return Collections.emptyList();
                })
                .map(HashSet::new)
                .await().atMost(Duration.ofSeconds(5));
    }
}
