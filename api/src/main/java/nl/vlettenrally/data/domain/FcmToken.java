package nl.vlettenrally.data.domain;

import java.time.Instant;
import java.util.UUID;

/**
 * FcmToken entity representing a Firebase Cloud Messaging token for push notifications.
 * Links to a DeviceRegistration.
 */
public class FcmToken {
    private UUID id;
    private UUID deviceRegistrationId;
    private String token;
    private Instant createdAt;
    private Instant updatedAt;

    public FcmToken() {
        this.id = UUID.randomUUID();
        this.createdAt = Instant.now();
        this.updatedAt = Instant.now();
    }

    public FcmToken(UUID id, UUID deviceRegistrationId, String token) {
        this();
        this.id = id;
        this.deviceRegistrationId = deviceRegistrationId;
        this.token = token;
    }

    // Getters and Setters
    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }

    public UUID getDeviceRegistrationId() { return deviceRegistrationId; }
    public void setDeviceRegistrationId(UUID deviceRegistrationId) { this.deviceRegistrationId = deviceRegistrationId; }

    public String getToken() { return token; }
    public void setToken(String token) { 
        this.token = token;
        this.updatedAt = Instant.now();
    }

    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }

    public Instant getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }
}
