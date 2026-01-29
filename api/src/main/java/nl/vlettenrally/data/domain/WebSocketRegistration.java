package nl.vlettenrally.data.domain;

import java.time.Instant;
import java.util.UUID;

/**
 * WebSocketRegistration entity representing an active or historical WebSocket connection.
 * Links to a DeviceRegistration to track connection state.
 */
public class WebSocketRegistration {
    private UUID id;
    private UUID deviceRegistrationId;
    private String sessionId;
    private Instant connectedAt;
    private Instant disconnectedAt;

    public WebSocketRegistration() {
        this.id = UUID.randomUUID();
        this.connectedAt = Instant.now();
    }

    public WebSocketRegistration(UUID id, UUID deviceRegistrationId, String sessionId) {
        this();
        this.id = id;
        this.deviceRegistrationId = deviceRegistrationId;
        this.sessionId = sessionId;
    }

    // Getters and Setters
    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }

    public UUID getDeviceRegistrationId() { return deviceRegistrationId; }
    public void setDeviceRegistrationId(UUID deviceRegistrationId) { this.deviceRegistrationId = deviceRegistrationId; }

    public String getSessionId() { return sessionId; }
    public void setSessionId(String sessionId) { this.sessionId = sessionId; }

    public Instant getConnectedAt() { return connectedAt; }
    public void setConnectedAt(Instant connectedAt) { this.connectedAt = connectedAt; }

    public Instant getDisconnectedAt() { return disconnectedAt; }
    public void setDisconnectedAt(Instant disconnectedAt) { this.disconnectedAt = disconnectedAt; }

    /**
     * Check if the WebSocket connection is currently active.
     */
    public boolean isConnected() {
        return disconnectedAt == null;
    }

    /**
     * Mark the connection as disconnected.
     */
    public void disconnect() {
        this.disconnectedAt = Instant.now();
    }
}
