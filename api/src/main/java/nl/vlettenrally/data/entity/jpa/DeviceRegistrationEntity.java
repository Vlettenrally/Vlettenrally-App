package nl.vlettenrally.data.entity.jpa;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

/**
 * JPA entity for DeviceRegistration.
 * Either teamId or adminId must be set (but not both).
 */
@Entity
@Table(name = "device_registrations", indexes = {
    @Index(name = "idx_device_registrations_team_id", columnList = "team_id"),
    @Index(name = "idx_device_registrations_admin_id", columnList = "admin_id"),
    @Index(name = "idx_device_registrations_last_seen", columnList = "last_seen")
})
public class DeviceRegistrationEntity {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "team_id")
    private TeamEntity team;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "admin_id")
    private AdminEntity admin;

    @Column(name = "last_seen", nullable = false)
    private Instant lastSeen;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    public DeviceRegistrationEntity() {
        this.id = UUID.randomUUID();
        this.createdAt = Instant.now();
        this.lastSeen = Instant.now();
    }

    /**
     * Updates the lastSeen timestamp to now.
     */
    public void touch() {
        this.lastSeen = Instant.now();
    }

    /**
     * Check if the device has been inactive for longer than the specified threshold.
     */
    public boolean isInactive(int thresholdMinutes) {
        if (lastSeen == null) {
            return true;
        }
        return lastSeen.plusSeconds(thresholdMinutes * 60L).isBefore(Instant.now());
    }

    // Getters and Setters
    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }

    public TeamEntity getTeam() { return team; }
    public void setTeam(TeamEntity team) { this.team = team; }

    public AdminEntity getAdmin() { return admin; }
    public void setAdmin(AdminEntity admin) { this.admin = admin; }

    public Instant getLastSeen() { return lastSeen; }
    public void setLastSeen(Instant lastSeen) { this.lastSeen = lastSeen; }

    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
}
