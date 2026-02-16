package nl.vlettenrally.websockets;

import java.time.Duration;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import io.quarkus.logging.Log;
import io.quarkus.redis.datasource.ReactiveRedisDataSource;
import io.quarkus.redis.datasource.value.ReactiveValueCommands;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

/**
 * Manages WebSocket session metadata in Valkey (Redis) for cross-pod session tracking.
 * <p>
 * This allows any pod to look up session information when handling PING requests,
 * even if the WebSocket connection is established on a different pod.
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
     */
    public void storeSession(WebSocketSessionMetadata metadata) {
        try {
            String key = SESSION_KEY_PREFIX + metadata.getSessionId();
            String json = objectMapper.writeValueAsString(metadata);
            
            ReactiveValueCommands<String, String> commands = redisDataSource.value(String.class);
            commands.setex(key, SESSION_TTL.getSeconds(), json)
                .subscribe().with(
                    success -> Log.debugf("Stored session metadata in Valkey: %s", metadata.getSessionId()),
                    error -> Log.errorf("Failed to store session metadata in Valkey: %s", error.getMessage())
                );
        } catch (JsonProcessingException e) {
            Log.errorf("Failed to serialize session metadata: %s", e.getMessage());
        }
    }
    
    /**
     * Retrieve session metadata from Valkey
     * Returns null if session not found or has expired
     */
    public WebSocketSessionMetadata getSession(String sessionId) {
        try {
            String key = SESSION_KEY_PREFIX + sessionId;
            ReactiveValueCommands<String, String> commands = redisDataSource.value(String.class);
            
            // Blocking call to get the value synchronously
            String json = commands.get(key).await().atMost(Duration.ofSeconds(1));
            
            if (json == null) {
                return null;
            }
            
            return objectMapper.readValue(json, WebSocketSessionMetadata.class);
        } catch (JsonProcessingException e) {
            Log.errorf("Failed to deserialize session metadata: %s", e.getMessage());
            return null;
        } catch (Exception e) {
            Log.errorf("Failed to retrieve session metadata from Valkey: %s", e.getMessage());
            return null;
        }
    }
    
    /**
     * Update session activity timestamp in Valkey
     * Resets the TTL to keep the session alive without retrieving the full metadata
     */
    public void updateActivity(String sessionId) {
        try {
            String key = SESSION_KEY_PREFIX + sessionId;
            
            // Simply refresh the TTL without reading/writing the full object
            redisDataSource.key().expire(key, SESSION_TTL)
                .subscribe().with(
                    success -> Log.debugf("Refreshed TTL for session %s: %s", sessionId, success),
                    error -> Log.errorf("Failed to refresh TTL for session %s: %s", sessionId, error.getMessage())
                );
        } catch (Exception e) {
            Log.errorf("Failed to update activity for session %s: %s", sessionId, e.getMessage());
        }
    }
    
    /**
     * Update game ID for admin sessions
     */
    public void updateGameId(String sessionId, java.util.UUID gameId) {
        WebSocketSessionMetadata metadata = getSession(sessionId);
        if (metadata != null && metadata.isAdmin()) {
            WebSocketSessionMetadata updated = metadata.withGameId(gameId);
            storeSession(updated);
        }
    }
    
    /**
     * Remove session metadata from Valkey
     */
    public void removeSession(String sessionId) {
        try {
            String key = SESSION_KEY_PREFIX + sessionId;
            ReactiveValueCommands<String, String> commands = redisDataSource.value(String.class);
            
            commands.getdel(key)
                .subscribe().with(
                    deleted -> Log.debugf("Removed session metadata from Valkey: %s", sessionId),
                    error -> Log.errorf("Failed to remove session metadata from Valkey: %s", error.getMessage())
                );
        } catch (Exception e) {
            Log.errorf("Failed to remove session from Valkey: %s", e.getMessage());
        }
    }
}
