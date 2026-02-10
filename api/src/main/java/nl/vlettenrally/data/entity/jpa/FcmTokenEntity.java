package nl.vlettenrally.data.entity.jpa;

import jakarta.persistence.*;
import lombok.Getter;

import java.time.Instant;
import java.util.UUID;

/**
 * JPA entity for FcmToken
 */
@Getter
@Entity
@Table(name = "fcm_tokens", indexes = {
    @Index(name = "idx_fcm_tokens_device_registration_id", columnList = "device_registration_id"),
    @Index(name = "idx_fcm_tokens_token", columnList = "token")
})
public class FcmTokenEntity {

    // Getters and Setters
    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "device_registration_id", nullable = false)
    private DeviceRegistrationEntity deviceRegistration;

    @Column(name = "token", nullable = false, columnDefinition = "TEXT")
    private String token;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    public FcmTokenEntity() {
        this.id = UUID.randomUUID();
        this.createdAt = Instant.now();
        this.updatedAt = Instant.now();
    }

    @PreUpdate
    protected void onUpdate() {
        this.updatedAt = Instant.now();
    }

    public void setId(UUID id) { this.id = id; }

    public void setDeviceRegistration(DeviceRegistrationEntity deviceRegistration) { this.deviceRegistration = deviceRegistration; }

    public void setToken(String token) {
        this.token = token;
        this.updatedAt = Instant.now();
    }

    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }

    public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }
}
