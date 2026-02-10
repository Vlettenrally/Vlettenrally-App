package nl.vlettenrally.data.domain;

import lombok.Getter;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

/**
 * WebSocketRegistration entity representing an active or historical WebSocket connection.
 * Links to a DeviceRegistration to track connection state.
 */
@Setter
@Getter
public class WebSocketRegistration {
    // Getters and Setters
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
