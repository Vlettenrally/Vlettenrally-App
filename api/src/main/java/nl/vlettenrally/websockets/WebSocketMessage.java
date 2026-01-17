package nl.vlettenrally.websockets;

import java.time.Instant;
import java.util.UUID;

/**
 * Represents a WebSocket message that can be sent to clients
 */
public class WebSocketMessage {
    private String type;
    private Object payload;
    private String timestamp;
    private UUID targetGameId;
    private UUID targetTeamId;
    private boolean adminOnly;

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

    // Getters and Setters
    public String getType() { return type; }
    public void setType(String type) { this.type = type; }

    public Object getPayload() { return payload; }
    public void setPayload(Object payload) { this.payload = payload; }

    public String getTimestamp() { return timestamp; }
    public void setTimestamp(String timestamp) { this.timestamp = timestamp; }

    public UUID getTargetGameId() { return targetGameId; }
    public void setTargetGameId(UUID targetGameId) { this.targetGameId = targetGameId; }

    public UUID getTargetTeamId() { return targetTeamId; }
    public void setTargetTeamId(UUID targetTeamId) { this.targetTeamId = targetTeamId; }

    public boolean isAdminOnly() { return adminOnly; }
    public void setAdminOnly(boolean adminOnly) { this.adminOnly = adminOnly; }
}
