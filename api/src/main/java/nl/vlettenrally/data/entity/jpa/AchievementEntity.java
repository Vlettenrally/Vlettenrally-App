package nl.vlettenrally.data.entity.jpa;

import jakarta.persistence.*;
import nl.vlettenrally.data.enums.AchievementStatus;

import java.time.Instant;
import java.util.UUID;

/**
 * JPA entity for Achievement
 */
@Entity
@Table(name = "achievements", indexes = {
    @Index(name = "idx_achievements_team_id", columnList = "team_id"),
    @Index(name = "idx_achievements_checkpoint_id", columnList = "checkpoint_id"),
    @Index(name = "idx_achievements_status", columnList = "status")
}, uniqueConstraints = {
    @UniqueConstraint(name = "uq_team_checkpoint", columnNames = {"team_id", "checkpoint_id"})
})
public class AchievementEntity {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "team_id", nullable = false)
    private TeamEntity team;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "checkpoint_id", nullable = false)
    private CheckpointEntity checkpoint;

    @Column(name = "points_earned", nullable = false)
    private int pointsEarned = 0;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private AchievementStatus status = AchievementStatus.PENDING;

    @Column(name = "detected_at", nullable = false)
    private Instant detectedAt;

    @Column(name = "verified_at")
    private Instant verifiedAt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "verified_by")
    private AdminEntity verifiedBy;

    @Column(name = "notes", columnDefinition = "TEXT")
    private String notes;

    public AchievementEntity() {
        this.id = UUID.randomUUID();
        this.detectedAt = Instant.now();
    }

    // Getters and Setters
    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }

    public TeamEntity getTeam() { return team; }
    public void setTeam(TeamEntity team) { this.team = team; }

    public CheckpointEntity getCheckpoint() { return checkpoint; }
    public void setCheckpoint(CheckpointEntity checkpoint) { this.checkpoint = checkpoint; }

    public int getPointsEarned() { return pointsEarned; }
    public void setPointsEarned(int pointsEarned) { this.pointsEarned = pointsEarned; }

    public AchievementStatus getStatus() { return status; }
    public void setStatus(AchievementStatus status) { this.status = status; }

    public Instant getDetectedAt() { return detectedAt; }
    public void setDetectedAt(Instant detectedAt) { this.detectedAt = detectedAt; }

    public Instant getVerifiedAt() { return verifiedAt; }
    public void setVerifiedAt(Instant verifiedAt) { this.verifiedAt = verifiedAt; }

    public AdminEntity getVerifiedBy() { return verifiedBy; }
    public void setVerifiedBy(AdminEntity verifiedBy) { this.verifiedBy = verifiedBy; }

    public String getNotes() { return notes; }
    public void setNotes(String notes) { this.notes = notes; }
}
