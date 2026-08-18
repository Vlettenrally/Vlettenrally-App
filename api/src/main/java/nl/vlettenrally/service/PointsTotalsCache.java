package nl.vlettenrally.service;

import io.quarkus.cache.CacheInvalidate;
import io.quarkus.cache.CacheResult;
import io.quarkus.logging.Log;
import io.quarkus.redis.datasource.RedisDataSource;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import nl.vlettenrally.data.repository.PointEntryRepository;

import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Two-tier cache for per-team points totals.
 * <p>
 * The ledger ({@code point_entries.current_amount}) is the authoritative source. On top of
 * it sit two caches:
 * <ul>
 *   <li><b>L1</b> — an in-process Caffeine cache ({@code "points-team-totals"}, very short TTL)
 *       applied by the {@link CacheResult} interceptor on {@link #get(UUID)}. It serves repeated
 *       reads of the <em>same</em> team — ranking and rival-payload bursts, back-to-back requests
 *       for one team — from local memory instead of Valkey.</li>
 *   <li><b>L2</b> — Valkey ({@code points:team:*}, {@link #TTL_SECONDS}). Shared across instances,
 *       written through on every ledger commit, so an L1 miss almost always resolves from Valkey
 *       rather than re-summing the ledger.</li>
 * </ul>
 * On a full miss the sum is re-derived from the ledger and written back to both tiers.
 * <p>
 * Serializing a whole list of teams touches a distinct key per team, so L1 cannot help there; use
 * {@link #getAll(Collection)}, which reads them in one MGET.
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
     * Totals for many teams at once, with the same semantics as {@link #get} per team: the Valkey
     * value when present, otherwise the ledger sum. Teams with no ledger entries map to 0, so the
     * returned map always has an entry for every id passed in.
     * <p>
     * Exists because serializing a list of teams needs every total at once, and each team is a
     * distinct L1 key — the in-process tier gives no dedup there, so calling {@link #get} per team
     * is one real Valkey round trip per team (250 of them for a full field). This collapses that
     * into one MGET plus, at most, one grouped ledger query for whatever the MGET missed.
     * <p>
     * Unlike {@link #get} this does <em>not</em> write misses back. A write-back would be one
     * {@code SETEX} per missing team — reintroducing the per-team fan-out this method exists to
     * remove — to save a single grouped query. Valkey is kept warm by the write-through on every
     * ledger commit and by {@link #get} on the single-team paths.
     */
    public Map<UUID, Integer> getAll(Collection<UUID> teamIds) {
        if (teamIds == null || teamIds.isEmpty()) {
            return Map.of();
        }
        Set<UUID> ids = new LinkedHashSet<>(teamIds);
        Map<UUID, Integer> totals = new HashMap<>(ids.size());

        try {
            String[] keys = ids.stream().map(id -> KEY_PREFIX + id).toArray(String[]::new);
            // MGET maps a missing key to a null value rather than omitting it.
            Map<String, String> cached = redis.value(String.class).mget(keys);
            for (UUID teamId : ids) {
                String value = cached.get(KEY_PREFIX + teamId);
                if (value == null) {
                    continue;
                }
                try {
                    totals.put(teamId, Integer.parseInt(value));
                } catch (NumberFormatException e) {
                    // Treat an unreadable entry as a miss — the ledger below is authoritative.
                }
            }
        } catch (Exception e) {
            Log.warnf("PointsTotalsCache bulk read failed for %d teams: %s", ids.size(), e.getMessage());
        }

        List<UUID> missing = ids.stream().filter(id -> !totals.containsKey(id)).toList();
        if (!missing.isEmpty()) {
            Map<UUID, Integer> summed = pointEntryRepository.sumCurrentAmountByTeams(missing);
            missing.forEach(teamId -> totals.put(teamId, summed.getOrDefault(teamId, 0)));
        }
        return totals;
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
