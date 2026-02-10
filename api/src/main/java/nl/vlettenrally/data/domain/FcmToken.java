package nl.vlettenrally.data.domain;

import lombok.Getter;

import java.time.Instant;
import java.util.UUID;

/**
 * FcmToken entity representing a Firebase Cloud Messaging token for push notifications.
 * Links to a DeviceRegistration.
 */
@Getter
public class FcmToken {
    // Getters and Setters
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

    public void setId(UUID id) { this.id = id; }

    public void setDeviceRegistrationId(UUID deviceRegistrationId) { this.deviceRegistrationId = deviceRegistrationId; }

    public void setToken(String token) {
        this.token = token;
        this.updatedAt = Instant.now();
    }

    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }

    public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }
}
