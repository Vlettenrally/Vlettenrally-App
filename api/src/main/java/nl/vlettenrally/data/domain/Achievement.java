package nl.vlettenrally.data.domain;

import java.time.Instant;
import java.util.UUID;

import lombok.Getter;
import lombok.Setter;
import nl.vlettenrally.data.enums.AchievementStatus;

/**
 * Achievement entity representing a team's checkpoint completion.
 * 
 * Flow:
 * 1. System detects team proximity to checkpoint → creates PENDING achievement
 * 2. Game master at checkpoint receives notification
 * 3. Game master verifies (VERIFIED) or rejects (REJECTED) the achievement
 * 4. Points are awarded only upon verification
 */
@Setter
@Getter
public class Achievement {
    // Getters and Setters
    private UUID id;
    private UUID teamId;
    private UUID checkpointId;
    private int pointsEarned;
    private AchievementStatus status;
    private Instant detectedAt;     // When system detected proximity
    private Instant verifiedAt;     // When game master verified/rejected
    private UUID verifiedBy;        // Game master who verified (future use)
    private String notes;           // Optional notes from game master

    public Achievement() {
        this.id = UUID.randomUUID();
        this.detectedAt = Instant.now();
        this.status = AchievementStatus.PENDING;
    }

    public Achievement(UUID id, UUID teamId, UUID checkpointId, int pointsEarned, AchievementStatus status, Instant detectedAt) {
        this.id = id;
        this.teamId = teamId;
        this.checkpointId = checkpointId;
        this.pointsEarned = pointsEarned;
        this.status = status;
        this.detectedAt = detectedAt;
    }

    // Convenience method for backward compatibility
    public Instant getAchievedAt() { 
        return status == AchievementStatus.VERIFIED ? verifiedAt : detectedAt; 
    }
}
