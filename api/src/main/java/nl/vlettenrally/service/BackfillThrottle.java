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
 * Valkey-backed throttling for the resource-intensive Legendstracking backfill endpoint,
 * shared by both trigger paths (the manual admin button and the poller's automatic gap
 * detection). Modelled on {@link PollingLeader} / {@link TokenRevocationStore}.
 * <p>
 * Two independent mechanisms per team:
 * <ul>
 *   <li><b>Cooldown</b> — a {@code SET NX EX} window that limits how <em>often</em> a team may be
 *       backfilled at all (default 15 min). A successful set means "allowed now, blocked again for
 *       the TTL".</li>
 *   <li><b>Lock</b> — a short {@code SET NX EX} lease held for the duration of one backfill run so a
 *       live poll tick and a backfill (or two backfills) cannot race the same team's distance
 *       accumulator or insert duplicate fixes.</li>
 * </ul>
 * On Valkey errors both fail <em>open</em> (allow the operation) and log, matching every other
 * Valkey caller in this codebase — the DB unique index on {@code (team_id, location_time)} and the
 * monotonic distance clamp remain as safety nets.
 */
@ApplicationScoped
public class BackfillThrottle {

    static final String COOLDOWN_KEY_PREFIX = "vlettenrally:backfill:cooldown:team:";
    static final String LOCK_KEY_PREFIX = "vlettenrally:backfill:lock:team:";

    /** Lock lease long enough to cover one backfill; auto-expires if the holder crashes mid-run. */
    static final long LOCK_TTL_SECONDS = 300L;

    @Inject
    RedisDataSource redisDataSource;

    @ConfigProperty(name = "vlettenrally.location.backfill.cooldown", defaultValue = "PT15M")
    Duration cooldown;

    private ValueCommands<String, String> values() {
        return redisDataSource.value(String.class);
    }

    private KeyCommands<String> keys() {
        return redisDataSource.key();
    }

    /**
     * Open a fresh cooldown window for the team. Returns true when the window was started (the
     * caller may proceed), false when the team is still cooling down. Fails open on Valkey errors.
     */
    public boolean tryStartCooldown(UUID teamId) {
        try {
            return values().setAndChanged(COOLDOWN_KEY_PREFIX + teamId, "1",
                new SetArgs().nx().ex(cooldown.toSeconds()));
        } catch (Exception e) {
            Log.errorf("Backfill cooldown check failed for team %s: %s — allowing", teamId, e.getMessage());
            return true;
        }
    }

    /** Seconds until the team's cooldown expires, or 0 if not cooling down (or on error). */
    public long cooldownRemainingSeconds(UUID teamId) {
        try {
            long ttl = keys().ttl(COOLDOWN_KEY_PREFIX + teamId);
            return ttl > 0 ? ttl : 0;
        } catch (Exception e) {
            Log.errorf("Backfill cooldown TTL read failed for team %s: %s", teamId, e.getMessage());
            return 0;
        }
    }

    /** Acquire the per-team backfill lock. Fails open (returns true) on Valkey errors. */
    public boolean tryAcquireLock(UUID teamId) {
        try {
            return values().setAndChanged(LOCK_KEY_PREFIX + teamId, "1",
                new SetArgs().nx().ex(LOCK_TTL_SECONDS));
        } catch (Exception e) {
            Log.errorf("Backfill lock acquire failed for team %s: %s — allowing", teamId, e.getMessage());
            return true;
        }
    }

    public void releaseLock(UUID teamId) {
        try {
            keys().del(LOCK_KEY_PREFIX + teamId);
        } catch (Exception e) {
            Log.debugf("Backfill lock release failed for team %s: %s (expires within %ds)",
                teamId, e.getMessage(), LOCK_TTL_SECONDS);
        }
    }

    /** True if a backfill is currently running for the team. Fails open (false) so the poller is never blocked. */
    public boolean isLocked(UUID teamId) {
        try {
            return keys().exists(LOCK_KEY_PREFIX + teamId);
        } catch (Exception e) {
            Log.debugf("Backfill lock existence check failed for team %s: %s", teamId, e.getMessage());
            return false;
        }
    }
}
