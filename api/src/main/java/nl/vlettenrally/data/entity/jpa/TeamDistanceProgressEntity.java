package nl.vlettenrally.data.entity.jpa;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

/**
 * JPA entity for per-team travelled-distance accumulator state. Keyed by team id
 * only — the engine always looks rows up by teamId. The anchor is stored as plain
 * lon/lat doubles (distance is summed in Java via Haversine), so no PostGIS column
 * is needed.
 */
@Setter
@Getter
@Entity
@Table(name = "team_distance_progress")
public class TeamDistanceProgressEntity {

    @Id
    @Column(name = "team_id", nullable = false, updatable = false)
    private UUID teamId;

    @Column(name = "anchor_lon")
    private Double anchorLon;

    @Column(name = "anchor_lat")
    private Double anchorLat;

    @Column(name = "cumulative_meters", nullable = false)
    private double cumulativeMeters;

    @Column(name = "last_processed_time")
    private Instant lastProcessedTime;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;
}
