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
 * JPA entity for one counted distance segment in a team's chain. Endpoints are
 * stored as plain lon/lat doubles (distance is computed in Java via Haversine),
 * so no PostGIS column is needed. Rows are insert/delete only — never updated.
 * The UNIQUE constraints on (team_id, from_time) and (team_id, to_time) enforce
 * chain integrity at the database level.
 */
@Setter
@Getter
@Entity
@Table(name = "team_distance_segment")
public class TeamDistanceSegmentEntity {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "team_id", nullable = false, updatable = false)
    private UUID teamId;

    @Column(name = "from_time", updatable = false)
    private Instant fromTime;

    @Column(name = "from_lon", updatable = false)
    private Double fromLon;

    @Column(name = "from_lat", updatable = false)
    private Double fromLat;

    @Column(name = "to_time", nullable = false, updatable = false)
    private Instant toTime;

    @Column(name = "to_lon", nullable = false, updatable = false)
    private double toLon;

    @Column(name = "to_lat", nullable = false, updatable = false)
    private double toLat;

    @Column(name = "meters", nullable = false, updatable = false)
    private double meters;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;
}
