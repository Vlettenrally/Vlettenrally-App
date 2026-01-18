package nl.vlettenrally.data.domain;

import java.time.Instant;
import java.util.UUID;

/**
 * Achievement entity representing a team's checkpoint completion.
 * 
 * Flow:
 * 1. System detects team proximity to checkpoint → creates PENDING achievement
 * 2. Game master at checkpoint receives notification
 * 3. Game master verifies (VERIFIED) or rejects (REJECTED) the achievement
 * 4. Points are awarded only upon verification
 */
public class Achievement {
    
    public enum Status {
        PENDING,    // Detected by system, awaiting game master verification
        VERIFIED,   // Confirmed by game master, points awarded
        REJECTED    // Rejected by game master (e.g., team didn't actually complete checkpoint)
    }
    
    private UUID id;
    private UUID teamId;
    private UUID checkpointId;
    private int pointsEarned;
    private Status status;
    private Instant detectedAt;     // When system detected proximity
    private Instant verifiedAt;     // When game master verified/rejected
    private UUID verifiedBy;        // Game master who verified (future use)
    private String notes;           // Optional notes from game master

    public Achievement() {
        this.id = UUID.randomUUID();
        this.detectedAt = Instant.now();
        this.status = Status.PENDING;
    }

    public Achievement(UUID id, UUID teamId, UUID checkpointId, int pointsEarned, Status status, Instant detectedAt) {
        this.id = id;
        this.teamId = teamId;
        this.checkpointId = checkpointId;
        this.pointsEarned = pointsEarned;
        this.status = status;
        this.detectedAt = detectedAt;
    }

    // Getters and Setters
    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }

    public UUID getTeamId() { return teamId; }
    public void setTeamId(UUID teamId) { this.teamId = teamId; }

    public UUID getCheckpointId() { return checkpointId; }
    public void setCheckpointId(UUID checkpointId) { this.checkpointId = checkpointId; }

    public int getPointsEarned() { return pointsEarned; }
    public void setPointsEarned(int pointsEarned) { this.pointsEarned = pointsEarned; }

    public Status getStatus() { return status; }
    public void setStatus(Status status) { this.status = status; }

    public Instant getDetectedAt() { return detectedAt; }
    public void setDetectedAt(Instant detectedAt) { this.detectedAt = detectedAt; }

    public Instant getVerifiedAt() { return verifiedAt; }
    public void setVerifiedAt(Instant verifiedAt) { this.verifiedAt = verifiedAt; }

    public UUID getVerifiedBy() { return verifiedBy; }
    public void setVerifiedBy(UUID verifiedBy) { this.verifiedBy = verifiedBy; }

    public String getNotes() { return notes; }
    public void setNotes(String notes) { this.notes = notes; }
    
    // Convenience method for backward compatibility
    public Instant getAchievedAt() { 
        return status == Status.VERIFIED ? verifiedAt : detectedAt; 
    }
}
