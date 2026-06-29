package nl.vlettenrally.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import io.quarkus.logging.Log;
import io.quarkus.redis.datasource.RedisDataSource;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import nl.vlettenrally.data.dto.auth.TeamOptionResponse;

import java.util.List;
import java.util.UUID;
import java.util.function.Supplier;

@ApplicationScoped
public class CheckInCacheService {

    static final String KEY_PREFIX = "check-in:teams:";
    static final long TTL_SECONDS = 15;

    @Inject
    RedisDataSource redis;

    @Inject
    ObjectMapper objectMapper;

    public List<TeamOptionResponse> getOrLoad(UUID gameId, Supplier<List<TeamOptionResponse>> loader) {
        String key = KEY_PREFIX + gameId;
        String cached = redis.value(String.class).get(key);
        if (cached != null) {
            try {
                return objectMapper.readValue(cached, new TypeReference<>() {});
            } catch (Exception ignored) {
                // Deserialization failure — fall through to DB
            }
        }
        List<TeamOptionResponse> teams = loader.get();
        try {
            redis.value(String.class).setex(key, TTL_SECONDS, objectMapper.writeValueAsString(teams));
        } catch (Exception e) {
            Log.warnf("check-in teams cache write failed: %s", e.getMessage());
        }
        return teams;
    }

    public void invalidate(UUID gameId) {
        redis.key().del(KEY_PREFIX + gameId);
    }
}
