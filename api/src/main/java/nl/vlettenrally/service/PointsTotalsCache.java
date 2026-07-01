package nl.vlettenrally.service;

import io.quarkus.logging.Log;
import io.quarkus.redis.datasource.RedisDataSource;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import nl.vlettenrally.data.repository.PointEntryRepository;

import java.util.UUID;

/**
 * Valkey-backed cache for per-team points totals.
 * <p>
 * The ledger ({@code point_entries.current_amount}) is the authoritative source.
 * This cache stores the pre-computed SUM so that every team-response and ranking
 * read avoids an aggregation query. On cache miss the sum is re-derived from the
 * ledger and then stored. A 10-minute safety TTL bounds staleness if a write is
 * somehow missed; normal operation always writes an explicit value after each
 * ledger commit via {@link #set(UUID, int)}.
 */
@ApplicationScoped
public class PointsTotalsCache {

    static final String KEY_PREFIX = "points:team:";
    static final long TTL_SECONDS = 600;

    @Inject
    RedisDataSource redis;

    @Inject
    PointEntryRepository pointEntryRepository;

    /**
     * Return the cached total for a team, falling back to a ledger SUM on miss.
     */
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
     * Write (or overwrite) the total for a team. Called after each ledger commit.
     */
    public void set(UUID teamId, int total) {
        String key = KEY_PREFIX + teamId;
        try {
            redis.value(String.class).setex(key, TTL_SECONDS, String.valueOf(total));
        } catch (Exception e) {
            Log.warnf("PointsTotalsCache write failed for team %s: %s", teamId, e.getMessage());
        }
    }
}
