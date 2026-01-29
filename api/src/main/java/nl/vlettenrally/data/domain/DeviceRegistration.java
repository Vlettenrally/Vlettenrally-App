package nl.vlettenrally.data.domain;

import java.time.Instant;
import java.util.UUID;

/**
 * DeviceRegistration entity representing a registered device for a team.
 * Tracks device type and last activity for determining message delivery channels.
 */
public class DeviceRegistration {
    private UUID id;
    private UUID teamId;
    private Instant lastSeen;
    private Instant createdAt;

    public DeviceRegistration() {
        this.id = UUID.randomUUID();
        this.createdAt = Instant.now();
        this.lastSeen = Instant.now();
    }

    public DeviceRegistration(UUID id, UUID teamId) {
        this();
        this.id = id;
        this.teamId = teamId;
    }

    // Getters and Setters
    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }

    public UUID getTeamId() { return teamId; }
    public void setTeamId(UUID teamId) { this.teamId = teamId; }

    public Instant getLastSeen() { return lastSeen; }
    public void setLastSeen(Instant lastSeen) { this.lastSeen = lastSeen; }

    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }

    /**
     * Updates the lastSeen timestamp to now.
     */
    public void touch() {
        this.lastSeen = Instant.now();
    }

    /**
     * Check if the device has been inactive for longer than the specified threshold.
     * @param thresholdMinutes the number of minutes after which a device is considered inactive
     * @return true if the device is inactive
     */
    public boolean isInactive(int thresholdMinutes) {
        if (lastSeen == null) {
            return true;
        }
        return lastSeen.plusSeconds(thresholdMinutes * 60L).isBefore(Instant.now());
    }
}
