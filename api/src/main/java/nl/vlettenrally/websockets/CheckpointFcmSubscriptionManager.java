package nl.vlettenrally.websockets;

import io.quarkus.logging.Log;
import io.quarkus.redis.datasource.ReactiveRedisDataSource;
import io.quarkus.redis.datasource.set.ReactiveSetCommands;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import java.time.Duration;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Tracks which admin users want to receive FCM checkpoint-detection notifications.
 * <p>
 * Unlike the heartbeat-based checkpoint topic in {@link AdminTopicSubscriptionManager}
 * (used for live WebSocket UI updates), this subscription is <strong>durable</strong>: it is only
 * removed by explicit unsubscribe calls. That allows operators to keep receiving
 * push notifications even when their phone screen locks or their browser
 * temporarily drops the WebSocket connection.
 * <p>
 * State is stored in Valkey sets keyed by {@code cp:fcm:{checkpointId}}, with
 * admin user IDs as members. The frontend issues subscribe/unsubscribe via WS
 * messages when the operator selects/changes/leaves a checkpoint.
 */
@ApplicationScoped
public class CheckpointFcmSubscriptionManager {

    private static final String KEY_PREFIX = "cp:fcm:";

    @Inject
    ReactiveRedisDataSource redisDataSource;

    public void subscribe(UUID checkpointId, UUID adminId) {
        if (checkpointId == null || adminId == null) return;
        ReactiveSetCommands<String, String> set = redisDataSource.set(String.class);
        set.sadd(KEY_PREFIX + checkpointId, adminId.toString())
            .subscribe().with(
                ok -> Log.debugf("FCM subscribed admin %s to checkpoint %s", adminId, checkpointId),
                err -> Log.errorf("Failed to add FCM subscription in Valkey: %s", err.getMessage())
            );
    }

    public void unsubscribe(UUID checkpointId, UUID adminId) {
        if (checkpointId == null || adminId == null) return;
        ReactiveSetCommands<String, String> set = redisDataSource.set(String.class);
        set.srem(KEY_PREFIX + checkpointId, adminId.toString())
            .subscribe().with(
                ok -> Log.debugf("FCM unsubscribed admin %s from checkpoint %s", adminId, checkpointId),
                err -> Log.errorf("Failed to remove FCM subscription in Valkey: %s", err.getMessage())
            );
    }

    public Set<UUID> getSubscribedAdmins(UUID checkpointId) {
        if (checkpointId == null) return Collections.emptySet();
        ReactiveSetCommands<String, String> set = redisDataSource.set(String.class);

        return set.smembers(KEY_PREFIX + checkpointId)
            .onFailure().recoverWithItem(err -> {
                Log.errorf("Failed to read FCM subscribers from Valkey: %s", err.getMessage());
                return Collections.emptySet();
            })
            .map(members -> members.stream()
                .map(s -> {
                    try { return UUID.fromString(s); }
                    catch (IllegalArgumentException e) { return null; }
                })
                .filter(java.util.Objects::nonNull)
                .collect(Collectors.toCollection(HashSet::new)))
            .map(s -> (Set<UUID>) s)
            .await().atMost(Duration.ofSeconds(5));
    }
}
