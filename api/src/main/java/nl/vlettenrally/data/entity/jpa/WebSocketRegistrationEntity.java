package nl.vlettenrally.data.entity.jpa;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

/**
 * JPA entity for WebSocketRegistration
 */
@Entity
@Table(name = "websocket_registrations", indexes = {
    @Index(name = "idx_websocket_registrations_device_registration_id", columnList = "device_registration_id"),
    @Index(name = "idx_websocket_registrations_session_id", columnList = "session_id")
})
public class WebSocketRegistrationEntity {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "device_registration_id", nullable = false)
    private DeviceRegistrationEntity deviceRegistration;

    @Column(name = "session_id", nullable = false)
    private String sessionId;

    @Column(name = "connected_at", nullable = false)
    private Instant connectedAt;

    @Column(name = "disconnected_at")
    private Instant disconnectedAt;

    public WebSocketRegistrationEntity() {
        this.id = UUID.randomUUID();
        this.connectedAt = Instant.now();
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

    // Getters and Setters
    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }

    public DeviceRegistrationEntity getDeviceRegistration() { return deviceRegistration; }
    public void setDeviceRegistration(DeviceRegistrationEntity deviceRegistration) { this.deviceRegistration = deviceRegistration; }

    public String getSessionId() { return sessionId; }
    public void setSessionId(String sessionId) { this.sessionId = sessionId; }

    public Instant getConnectedAt() { return connectedAt; }
    public void setConnectedAt(Instant connectedAt) { this.connectedAt = connectedAt; }

    public Instant getDisconnectedAt() { return disconnectedAt; }
    public void setDisconnectedAt(Instant disconnectedAt) { this.disconnectedAt = disconnectedAt; }
}
