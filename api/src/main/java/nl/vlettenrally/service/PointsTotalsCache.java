package nl.vlettenrally.service;

import io.quarkus.cache.CacheInvalidate;
import io.quarkus.cache.CacheResult;
import io.quarkus.logging.Log;
import io.quarkus.redis.datasource.RedisDataSource;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import nl.vlettenrally.data.repository.PointEntryRepository;

import java.util.UUID;

/**
 * Two-tier cache for per-team points totals.
 * <p>
 * The ledger ({@code point_entries.current_amount}) is the authoritative source. On top of
 * it sit two caches:
 * <ul>
 *   <li><b>L1</b> — an in-process Caffeine cache ({@code "points-team-totals"}, very short TTL)
 *       applied by the {@link CacheResult} interceptor on {@link #get(UUID)}. It collapses the
 *       per-team / per-rival fan-out that ranking, rival-payload and team-list serialization do
 *       within a single request or broadcast burst into local-memory hits — no Valkey round-trip
 *       per team.</li>
 *   <li><b>L2</b> — Valkey ({@code points:team:*}, {@link #TTL_SECONDS}). Shared across instances,
 *       written through on every ledger commit, so an L1 miss almost always resolves from Valkey
 *       rather than re-summing the ledger.</li>
 * </ul>
 * On a full miss the sum is re-derived from the ledger and written back to both tiers.
 * <p>
 * The {@link CacheResult}/{@link CacheInvalidate} interceptors only fire when these methods are
 * invoked through the CDI proxy from another bean (not on self-invocation) — which is exactly how
 * every caller uses them. {@link #get}'s internal call to {@link #set} on a miss therefore does not
 * self-invalidate; the interceptor simply caches the value {@code get} returns.
 * <p>
 * Multi-instance note: after a write, a <em>different</em> instance's L1 lags the new value by at
 * most the L1 TTL. This is acceptable because totals are already eventually consistent and the
 * authoritative value is carried on the RANKING_UPDATE / RIVAL_UPDATE payloads that follow a change.
 */
@ApplicationScoped
public class PointsTotalsCache {

    static final String KEY_PREFIX = "points:team:";
    static final long TTL_SECONDS = 600;
    static final String L1_CACHE = "points-team-totals";

    @Inject
    RedisDataSource redis;

    @Inject
    PointEntryRepository pointEntryRepository;

    /**
     * Return the cached total for a team. Served from the in-process L1 cache when warm;
     * otherwise falls back to Valkey and, on a Valkey miss, to a ledger SUM (written back to
     * both tiers).
     */
    @CacheResult(cacheName = L1_CACHE)
    public int get(UUID teamId) {
        String key = KEY_PREFIX + teamId;
        try {
            String cached = redis.value(String.class).get(key);
            if (cached != null) {
                return Integer.parseInt(cached);
            }
        } catch (Exception e) {
            Log.warnf("PointsTotalsCache read failed for team %s: %s", teamId, e.getMessage());
        }
        int total = pointEntryRepository.sumCurrentAmountByTeam(teamId);
        set(teamId, total);
        return total;
    }

    /**
     * Write (or overwrite) the total for a team. Called after each ledger commit. Evicts the L1
     * entry (via {@link CacheInvalidate}) so the next {@link #get} re-reads the value just written
     * to Valkey, and writes the new value through to Valkey.
     */
    @CacheInvalidate(cacheName = L1_CACHE)
    public void set(UUID teamId, int total) {
        String key = KEY_PREFIX + teamId;
        try {
            redis.value(String.class).setex(key, TTL_SECONDS, String.valueOf(total));
        } catch (Exception e) {
            Log.warnf("PointsTotalsCache write failed for team %s: %s", teamId, e.getMessage());
        }
    }
}
