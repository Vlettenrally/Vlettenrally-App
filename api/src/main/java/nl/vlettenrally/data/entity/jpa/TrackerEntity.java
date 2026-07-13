package nl.vlettenrally.data.entity.jpa;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

/**
 * One physical GPS tracker in the global pool. Membership and health status only;
 * the team it is assigned to (if any) lives on {@code teams.tracker_id}. Status
 * columns are refreshed by the location poller for trackers seen in a game feed.
 */
@Setter
@Getter
@Entity
@Table(
    name = "trackers",
    uniqueConstraints = @UniqueConstraint(name = "trackers_tracker_id_key", columnNames = "tracker_id")
)
public class TrackerEntity {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id = UUID.randomUUID();

    @Column(name = "tracker_id", nullable = false, length = 64)
    private String trackerId;

    /** Disabled trackers are skipped when assigning trackers to teams. */
    @Column(name = "enabled", nullable = false)
    private boolean enabled = true;

    @Column(name = "battery_percentage")
    private Integer batteryPercentage;

    @Column(name = "last_seen_at")
    private Instant lastSeenAt;

    @Column(name = "last_latitude")
    private Double lastLatitude;

    @Column(name = "last_longitude")
    private Double lastLongitude;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();
}
