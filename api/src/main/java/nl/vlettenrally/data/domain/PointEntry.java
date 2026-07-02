package nl.vlettenrally.data.domain;

import lombok.Getter;
import lombok.Setter;
import nl.vlettenrally.data.enums.PointEntryType;
import org.locationtech.jts.geom.Point;

import java.time.Instant;
import java.util.UUID;

/**
 * Domain model for an entry in the points ledger.
 * <p>
 * Every change to a Team's totalPoints produces one PointEntry.
 * An entry's {@code originalAmount} is set at creation and never changes;
 * {@code currentAmount} may be adjusted later (admin penalty or correction).
 * The UI projects the deducted delta as {@code originalAmount - currentAmount}.
 */
@Getter
@Setter
public class PointEntry {
    private UUID id;
    private UUID teamId;
    private UUID gameId;
    private PointEntryType entryType;
    private int originalAmount;     // immutable after creation
    private int currentAmount;      // mutable via editEntry
    private Double distanceMeters;  // nullable; credited distance for DISTANCE_BONUS entries
    private UUID sourceAchievementId;   // nullable; set for CHECKPOINT_VERIFICATION
    private Point location;             // nullable; WGS84
    private String description;
    private Instant createdAt;
    private Instant updatedAt;          // last modified (admin edit or system accrual)
    private UUID createdBy;             // nullable; set for MANUAL_ADJUSTMENT
    private Instant editedAt;           // nullable
    private UUID editedBy;              // nullable
    private String editReason;          // nullable

    public PointEntry() {
        this.id = UUID.randomUUID();
        this.createdAt = Instant.now();
        this.updatedAt = this.createdAt;
    }
}
