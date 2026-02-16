package nl.vlettenrally.websockets;

import java.time.Instant;
import java.util.UUID;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Metadata for a WebSocket session stored in Valkey for cross-pod session tracking.
 * This allows any pod to look up session information and send responses via pub/sub.
 */
public class WebSocketSessionMetadata {
    
    private final String sessionId;
    private final String sessionType; // "TEAM" or "ADMIN"
    private final UUID gameId;
    private final UUID teamId; // null for admin sessions
    private final Instant lastActivity;
    
    @JsonCreator
    public WebSocketSessionMetadata(
            @JsonProperty("sessionId") String sessionId,
            @JsonProperty("sessionType") String sessionType,
            @JsonProperty("gameId") UUID gameId,
            @JsonProperty("teamId") UUID teamId,
            @JsonProperty("lastActivity") Instant lastActivity) {
        this.sessionId = sessionId;
        this.sessionType = sessionType;
        this.gameId = gameId;
        this.teamId = teamId;
        this.lastActivity = lastActivity != null ? lastActivity : Instant.now();
    }
    
    public static WebSocketSessionMetadata forTeam(String sessionId, UUID gameId, UUID teamId) {
        return new WebSocketSessionMetadata(sessionId, "TEAM", gameId, teamId, Instant.now());
    }
    
    public static WebSocketSessionMetadata forAdmin(String sessionId, UUID gameId) {
        return new WebSocketSessionMetadata(sessionId, "ADMIN", gameId, null, Instant.now());
    }
    
    public static WebSocketSessionMetadata forAdminNoGame(String sessionId) {
        return new WebSocketSessionMetadata(sessionId, "ADMIN", null, null, Instant.now());
    }
    
    public String getSessionId() {
        return sessionId;
    }
    
    public String getSessionType() {
        return sessionType;
    }
    
    public UUID getGameId() {
        return gameId;
    }
    
    public UUID getTeamId() {
        return teamId;
    }
    
    public Instant getLastActivity() {
        return lastActivity;
    }
    
    public boolean isTeam() {
        return "TEAM".equals(sessionType);
    }
    
    public boolean isAdmin() {
        return "ADMIN".equals(sessionType);
    }
    
    /**
     * Create a new metadata instance with updated activity timestamp
     */
    public WebSocketSessionMetadata withUpdatedActivity() {
        return new WebSocketSessionMetadata(sessionId, sessionType, gameId, teamId, Instant.now());
    }
    
    /**
     * Create a new metadata instance with updated game ID (for admin game switching)
     */
    public WebSocketSessionMetadata withGameId(UUID newGameId) {
        return new WebSocketSessionMetadata(sessionId, sessionType, newGameId, teamId, lastActivity);
    }
}
