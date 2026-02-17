package nl.vlettenrally.websockets;

import java.time.Instant;
import java.util.UUID;

import io.quarkus.runtime.annotations.RegisterForReflection;
import lombok.Getter;
import lombok.Setter;

/**
 * Represents a WebSocket message that can be sent to clients
 */
@Setter
@Getter
@RegisterForReflection
public class WebSocketMessage {
    // Getters and Setters
    private String type;
    private Object payload;
    private String timestamp;
    private UUID targetGameId;
    private UUID targetTeamId;
    private boolean adminOnly;
    private String targetSessionId; // For session-specific messages like PONG

    public WebSocketMessage() {
        this.timestamp = Instant.now().toString();
    }

    public WebSocketMessage(String type, Object payload) {
        this();
        this.type = type;
        this.payload = payload;
    }

    // Builder-style methods for fluent API
    public WebSocketMessage withTargetGame(UUID gameId) {
        this.targetGameId = gameId;
        return this;
    }

    public WebSocketMessage withTargetTeam(UUID teamId) {
        this.targetTeamId = teamId;
        return this;
    }

    public WebSocketMessage forAdminOnly() {
        this.adminOnly = true;
        return this;
    }
    
    public WebSocketMessage withTargetSession(String sessionId) {
        this.targetSessionId = sessionId;
        return this;
    }

}
