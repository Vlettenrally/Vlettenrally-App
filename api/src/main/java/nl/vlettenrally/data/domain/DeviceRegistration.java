package nl.vlettenrally.data.domain;

import lombok.Getter;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

/**
 * DeviceRegistration entity representing a registered device for a team or admin.
 * Tracks device type and last activity for determining message delivery channels.
 * Either teamId or adminId must be set (but not both).
 */
@Setter
@Getter
public class DeviceRegistration {
    // Getters and Setters
    private UUID id;
    private UUID teamId;
    private UUID adminId;
    private UUID gameId;
    private String nickname;
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

    public DeviceRegistration(UUID id, UUID teamId, UUID gameId, String nickname) {
        this(id, teamId);
        this.gameId = gameId;
        this.nickname = nickname;
    }

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
