package nl.vlettenrally.data.entity.jpa;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import nl.vlettenrally.data.enums.PointEntryType;
import org.locationtech.jts.geom.Point;

import java.time.Instant;
import java.util.UUID;

/**
 * JPA entity for an entry in the points ledger.
 * One row per change to a team's totalPoints. {@link #originalAmount} is immutable;
 * {@link #currentAmount} may be edited (admin "Aftrek" / correction).
 */
@Setter
@Getter
@Entity
@Table(name = "point_entries", indexes = {
    @Index(name = "idx_point_entries_game_created", columnList = "game_id, created_at"),
    @Index(name = "idx_point_entries_team_created", columnList = "team_id, created_at")
})
public class PointEntryEntity {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "team_id", nullable = false)
    private TeamEntity team;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "game_id", nullable = false)
    private GameEntity game;

    @Enumerated(EnumType.STRING)
    @Column(name = "entry_type", nullable = false, length = 40)
    private PointEntryType entryType;

    @Column(name = "original_amount", nullable = false)
    private int originalAmount;

    @Column(name = "current_amount", nullable = false)
    private int currentAmount;

    @Column(name = "source_achievement_id")
    private UUID sourceAchievementId;

    @Column(name = "location", columnDefinition = "geometry(Point, 4326)")
    private Point location;

    @Column(name = "description", columnDefinition = "TEXT")
    private String description;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "created_by")
    private UUID createdBy;

    @Column(name = "edited_at")
    private Instant editedAt;

    @Column(name = "edited_by")
    private UUID editedBy;

    @Column(name = "edit_reason", columnDefinition = "TEXT")
    private String editReason;

    public PointEntryEntity() {
        this.id = UUID.randomUUID();
        this.createdAt = Instant.now();
    }
}
