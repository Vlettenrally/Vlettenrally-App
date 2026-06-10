package nl.vlettenrally.service;

import io.quarkus.logging.Log;
import io.quarkus.redis.datasource.RedisDataSource;
import io.quarkus.redis.datasource.keys.KeyCommands;
import io.quarkus.redis.datasource.value.SetArgs;
import io.quarkus.redis.datasource.value.ValueCommands;
import jakarta.annotation.PreDestroy;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.eclipse.microprofile.config.inject.ConfigProperty;

import java.util.UUID;

/**
 * Renewable Valkey-backed lease that elects a single API instance to run polling work.
 *
 * Each replica generates a random owner id at startup. {@link #tryAcquireOrRenew()}
 * is called once per scheduler tick: a successful return means this instance is the
 * leader for the next {@code TTL} seconds and should perform the work. If the leader
 * crashes the lock expires within {@code TTL} seconds and another instance takes over.
 *
 * TTL is set to 3× the polling interval (90s for the default 30s cadence) so a single
 * missed tick does not trigger failover.
 */
@ApplicationScoped
public class PollingLeader {

    static final String LOCK_KEY = "vlettenrally:scheduler:location-polling:leader";
    static final long LOCK_TTL_SECONDS = 90L;

    private final String ownerId = UUID.randomUUID().toString();

    @Inject
    RedisDataSource redisDataSource;

    @ConfigProperty(name = "vlettenrally.scheduler.lock.enabled", defaultValue = "true")
    boolean lockEnabled;

    public boolean tryAcquireOrRenew() {
        if (!lockEnabled) {
            return true;
        }
        try {
            ValueCommands<String, String> values = redisDataSource.value(String.class);
            boolean acquired = values.setAndChanged(LOCK_KEY, ownerId, new SetArgs().nx().ex(LOCK_TTL_SECONDS));
            if (acquired) {
                Log.infof("Polling leader acquired by %s", ownerId);
                return true;
            }
            String current = values.get(LOCK_KEY);
            if (ownerId.equals(current)) {
                values.setAndChanged(LOCK_KEY, ownerId, new SetArgs().xx().ex(LOCK_TTL_SECONDS));
                return true;
            }
            Log.debugf("Polling leader held by %s, this instance %s skipping tick", current, ownerId);
            return false;
        } catch (Exception e) {
            Log.errorf("Polling leader election failed: %s — skipping tick to avoid double-polling", e.getMessage());
            return false;
        }
    }

    @PreDestroy
    void release() {
        if (!lockEnabled) {
            return;
        }
        try {
            ValueCommands<String, String> values = redisDataSource.value(String.class);
            String current = values.get(LOCK_KEY);
            if (ownerId.equals(current)) {
                KeyCommands<String> keys = redisDataSource.key();
                keys.del(LOCK_KEY);
                Log.infof("Polling leader %s released the lock", ownerId);
            }
        } catch (Exception e) {
            Log.warnf("Failed to release polling leader lock: %s", e.getMessage());
        }
    }

    String getOwnerId() {
        return ownerId;
    }
}
