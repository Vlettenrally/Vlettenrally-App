package nl.vlettenrally.data.entity.jpa;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import nl.vlettenrally.data.enums.AchievementStatus;

import java.time.Instant;
import java.util.UUID;

/**
 * JPA entity for Achievement
 */
@Setter
@Getter
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

}
