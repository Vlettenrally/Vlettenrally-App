package nl.vlettenrally.service;

import io.quarkus.logging.Log;
import io.quarkus.redis.datasource.RedisDataSource;
import io.quarkus.redis.datasource.keys.KeyCommands;
import io.quarkus.redis.datasource.value.SetArgs;
import io.quarkus.redis.datasource.value.ValueCommands;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.eclipse.microprofile.config.inject.ConfigProperty;

import java.time.Duration;
import java.util.UUID;

/**
 * Valkey-backed per-team cooldown that limits how often a team may change its rival list.
 * Modelled on the cooldown half of {@link BackfillThrottle}.
 * <p>
 * A single {@code SET NX EX} window per team: {@link #tryStartCooldown} both checks and opens
 * the window atomically, so it is safe across API instances without a lock. A successful set
 * means "allowed now, blocked again for the TTL". On Valkey errors it fails <em>open</em>
 * (allows the change) and logs, matching every other Valkey caller in this codebase.
 */
@ApplicationScoped
public class RivalsCooldown {

    static final String COOLDOWN_KEY_PREFIX = "vlettenrally:rivals:cooldown:team:";

    @Inject
    RedisDataSource redisDataSource;

    @ConfigProperty(name = "vlettenrally.rivals.cooldown", defaultValue = "PT10S")
    Duration cooldown;

    private ValueCommands<String, String> values() {
        return redisDataSource.value(String.class);
    }

    private KeyCommands<String> keys() {
        return redisDataSource.key();
    }

    /**
     * Open a fresh cooldown window for the team. Returns true when the window was started (the
     * caller may proceed with the rival change), false when the team is still cooling down.
     * Fails open on Valkey errors.
     */
    public boolean tryStartCooldown(UUID teamId) {
        try {
            return values().setAndChanged(COOLDOWN_KEY_PREFIX + teamId, "1",
                new SetArgs().nx().ex(cooldown.toSeconds()));
        } catch (Exception e) {
            Log.errorf("Rivals cooldown check failed for team %s: %s — allowing", teamId, e.getMessage());
            return true;
        }
    }

    /** Seconds until the team's cooldown expires, or 0 if not cooling down (or on error). */
    public long cooldownRemainingSeconds(UUID teamId) {
        try {
            long ttl = keys().ttl(COOLDOWN_KEY_PREFIX + teamId);
            return ttl > 0 ? ttl : 0;
        } catch (Exception e) {
            Log.errorf("Rivals cooldown TTL read failed for team %s: %s", teamId, e.getMessage());
            return 0;
        }
    }
}
