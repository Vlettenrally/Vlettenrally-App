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
 * Tracks which admin WebSocket sessions are currently manning a given checkpoint
 * (i.e. have it selected on the checkpoint-station page).
 * <p>
 * Mirrors {@link MapSubscriptionManager}: state is stored in Valkey sorted sets
 * keyed by {@code cp:subs:{checkpointId}}, with sessionIds as members and the
 * heartbeat epoch-second as the score. Stale entries (no heartbeat in 30 s) are
 * pruned at lookup time.
 */
@ApplicationScoped
public class CheckpointSubscriptionManager {

    private static final String KEY_PREFIX = "cp:subs:";
    private static final Duration HEARTBEAT_TIMEOUT = Duration.ofSeconds(30);

    @Inject
    ReactiveRedisDataSource redisDataSource;

    /**
     * Register or refresh a checkpoint subscription for an admin session.
     * Called when CHECKPOINT_HEARTBEAT is received.
     */
    public void subscribe(String sessionId, UUID checkpointId) {
        if (checkpointId == null) {
            Log.debugf("Ignoring checkpoint subscription for session %s: no checkpoint selected", sessionId);
            return;
        }

        String key = KEY_PREFIX + checkpointId;
        double score = Instant.now().getEpochSecond();

        ReactiveSortedSetCommands<String, String> set = redisDataSource.sortedSet(String.class);
        set.zadd(key, score, sessionId)
                .chain(() -> redisDataSource.key().expire(key, HEARTBEAT_TIMEOUT.multipliedBy(2)))
                .subscribe().with(
                        ok -> Log.debugf("Checkpoint subscription updated for session %s (checkpoint %s)", sessionId, checkpointId),
                        err -> Log.errorf("Failed to update checkpoint subscription in Valkey: %s", err.getMessage())
                );
    }

    /**
     * Remove a checkpoint subscription for an admin session from a specific checkpoint.
     */
    public void unsubscribe(String sessionId, UUID checkpointId) {
        if (checkpointId == null) return;

        String key = KEY_PREFIX + checkpointId;
        ReactiveSortedSetCommands<String, String> set = redisDataSource.sortedSet(String.class);
        set.zrem(key, sessionId)
                .subscribe().with(
                        removed -> Log.debugf("Checkpoint subscription removed for session %s (checkpoint %s)", sessionId, checkpointId),
                        err -> Log.errorf("Failed to remove checkpoint subscription from Valkey: %s", err.getMessage())
                );
    }

    /**
     * Get all active admin session IDs subscribed to a checkpoint.
     * Prunes stale entries (score older than heartbeat timeout) in the same call.
     */
    public Set<String> getActiveSubscribers(UUID checkpointId) {
        String key = KEY_PREFIX + checkpointId;
        double cutoff = Instant.now().minus(HEARTBEAT_TIMEOUT).getEpochSecond();

        ReactiveSortedSetCommands<String, String> set = redisDataSource.sortedSet(String.class);

        return set.zremrangebyscore(key, ScoreRange.from(Double.NEGATIVE_INFINITY, cutoff))
                .chain(() -> set.zrangebyscore(key, ScoreRange.from(cutoff, Double.POSITIVE_INFINITY)))
                .onFailure().recoverWithItem(err -> {
                    Log.errorf("Failed to get checkpoint subscribers from Valkey: %s", err.getMessage());
                    return Collections.<String>emptyList();
                })
                .map(HashSet::new)
                .await().atMost(Duration.ofSeconds(5));
    }
}
