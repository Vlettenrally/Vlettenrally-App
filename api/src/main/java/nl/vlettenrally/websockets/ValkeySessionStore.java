package nl.vlettenrally.websockets;

import java.time.Duration;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import io.quarkus.logging.Log;
import io.quarkus.redis.datasource.ReactiveRedisDataSource;
import io.quarkus.redis.datasource.value.ReactiveValueCommands;
import io.smallrye.mutiny.Uni;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

/**
 * Manages WebSocket session metadata in Valkey (Redis) for cross-pod session tracking.
 * <p>
 * This allows any pod to look up session information when handling PING requests,
 * even if the WebSocket connection is established on a different pod.
 * <p>
 * All methods return Uni for reactive programming. Callers should subscribe to complete the operation.
 */
@ApplicationScoped
public class ValkeySessionStore {
    
    private static final String SESSION_KEY_PREFIX = "ws:session:";
    private static final Duration SESSION_TTL = Duration.ofSeconds(60); // Expire after 60s of inactivity
    
    @Inject
    ReactiveRedisDataSource redisDataSource;
    
    @Inject
    ObjectMapper objectMapper;
    
    /**
     * Store session metadata in Valkey with automatic expiration
     * @return Uni that completes when the operation is done
     */
    public Uni<Void> storeSession(WebSocketSessionMetadata metadata) {
        try {
            String key = SESSION_KEY_PREFIX + metadata.sessionId();
            String json = objectMapper.writeValueAsString(metadata);
            
            ReactiveValueCommands<String, String> commands = redisDataSource.value(String.class);
            return commands.setex(key, SESSION_TTL.getSeconds(), json)
                .onItem().invoke(() -> Log.debugf("Stored session metadata in Valkey: %s", metadata.sessionId()))
                .onFailure().invoke(error -> Log.errorf("Failed to store session metadata in Valkey: %s", error.getMessage()))
                .replaceWithVoid();
        } catch (JsonProcessingException e) {
            Log.errorf("Failed to serialize session metadata: %s", e.getMessage());
            return Uni.createFrom().voidItem();
        }
    }
    
    /**
     * Retrieve session metadata from Valkey
     * @return Uni containing the session metadata, or null if not found or expired
     */
    public Uni<WebSocketSessionMetadata> getSession(String sessionId) {
        String key = SESSION_KEY_PREFIX + sessionId;
        ReactiveValueCommands<String, String> commands = redisDataSource.value(String.class);
        
        return commands.get(key)
            .onItem().transform(json -> {
                if (json == null) {
                    return null;
                }
                try {
                    return objectMapper.readValue(json, WebSocketSessionMetadata.class);
                } catch (JsonProcessingException e) {
                    Log.errorf("Failed to deserialize session metadata: %s", e.getMessage());
                    return null;
                }
            })
            .onFailure().recoverWithItem((Throwable error) -> {
                Log.errorf("Failed to retrieve session metadata from Valkey: %s", error.getMessage());
                return null;
            });
    }
    
    /**
     * Update session activity timestamp in Valkey
     * Resets the TTL to keep the session alive without retrieving the full metadata
     * @return Uni that completes when the operation is done
     */
    public Uni<Void> updateActivity(String sessionId) {
        String key = SESSION_KEY_PREFIX + sessionId;
        
        return redisDataSource.key().expire(key, SESSION_TTL)
            .onItem().invoke(success -> Log.debugf("Refreshed TTL for session %s: %s", sessionId, success))
            .onFailure().invoke(error -> Log.errorf("Failed to refresh TTL for session %s: %s", sessionId, error.getMessage()))
            .replaceWithVoid();
    }
    
    /**
     * Update game ID for admin sessions
     * @return Uni that completes when the operation is done
     */
    public Uni<Void> updateGameId(String sessionId, java.util.UUID gameId) {
        return getSession(sessionId)
            .onItem().transformToUni(metadata -> {
                if (metadata != null && metadata.isAdmin()) {
                    WebSocketSessionMetadata updated = metadata.withGameId(gameId);
                    return storeSession(updated);
                }
                return Uni.createFrom().voidItem();
            });
    }
    
    /**
     * Remove session metadata from Valkey
     * @return Uni that completes when the operation is done
     */
    public Uni<Void> removeSession(String sessionId) {
        String key = SESSION_KEY_PREFIX + sessionId;
        ReactiveValueCommands<String, String> commands = redisDataSource.value(String.class);
        
        return commands.getdel(key)
            .onItem().invoke(() -> Log.debugf("Removed session metadata from Valkey: %s", sessionId))
            .onFailure().invoke(error -> Log.errorf("Failed to remove session metadata from Valkey: %s", error.getMessage()))
            .replaceWithVoid();
    }
}
