package nl.vlettenrally.data.entity.jpa;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.locationtech.jts.geom.Point;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * JPA entity for LocationData stored in TimescaleDB hypertable.
 * Represents a single location update from a team's tracker.
 *
 * Uses PostGIS Point geometry for efficient spatial queries (geofencing, proximity detection).
 * Stored in a TimescaleDB hypertable with automatic:
 * - Compression: chunks older than 1 hour are compressed to 5-minute intervals
 * - Retention: data older than 1 day is automatically deleted
 *
 * Primary Key: Composite (id, location_time)
 * - id: Unique identifier for the location record
 * - location_time: TimescaleDB partitioning column (required for hypertables)
 */
@Setter
@Getter
@Entity
@Table(name = "location_data", indexes = {
    @Index(name = "idx_location_data_location", columnList = "location"),
    @Index(name = "idx_location_data_team_time", columnList = "team_id, location_time"),
    @Index(name = "idx_location_data_game_time", columnList = "game_id, location_time")
})
@IdClass(LocationDataId.class)
public class LocationDataEntity {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Id
    @Column(name = "location_time", nullable = false)
    private Instant locationTime;  // Part of primary key for TimescaleDB partitioning

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "team_id", nullable = false)
    private TeamEntity team;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "game_id", nullable = false)
    private GameEntity game;

    @Column(name = "location", nullable = false, columnDefinition = "geometry(Point, 4326)")
    private Point location;  // PostGIS Point with WGS84 coordinates (longitude, latitude)


    @Column(name = "accuracy")
    private BigDecimal accuracy;  // GPS accuracy in meters

    @Column(name = "altitude")
    private BigDecimal altitude;  // Altitude in meters

    @Column(name = "speed")
    private BigDecimal speed;  // Speed in km/h

    @Column(name = "heading")
    private BigDecimal heading;  // Heading/bearing in degrees

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    public LocationDataEntity() {
        this.id = UUID.randomUUID();
        this.createdAt = Instant.now();
    }

    public LocationDataEntity(UUID id, TeamEntity team, GameEntity game, Point location,
                              Instant locationTime, BigDecimal accuracy, BigDecimal altitude,
                              BigDecimal speed, BigDecimal heading) {
        this.id = id;
        this.team = team;
        this.game = game;
        this.location = location;
        this.locationTime = locationTime;
        this.accuracy = accuracy;
        this.altitude = altitude;
        this.speed = speed;
        this.heading = heading;
        this.createdAt = Instant.now();
    }
}

