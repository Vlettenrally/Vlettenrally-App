package nl.vlettenrally.data.entity.jpa;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.locationtech.jts.geom.Point;

import java.time.Instant;
import java.util.UUID;

/**
 * JPA entity for Checkpoint
 */
@Setter
@Getter
@Entity
@Table(name = "checkpoints", indexes = {
    @Index(name = "idx_checkpoints_game_id", columnList = "game_id"),
    @Index(name = "idx_checkpoints_opening_time", columnList = "opening_time"),
    @Index(name = "idx_checkpoints_game_external_id", columnList = "game_id, external_id")
})
public class CheckpointEntity {

    // Getters and Setters
    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "game_id", nullable = false)
    private GameEntity game;

    @Column(name = "name", nullable = false)
    private String name;

    @Column(name = "opening_time")
    private Instant openingTime;

    @Column(name = "closing_time")
    private Instant closingTime;

    @Column(name = "points", nullable = false)
    private int points = 0;

    @Column(name = "latitude")
    private Double latitude;

    @Column(name = "longitude")
    private Double longitude;

    @Column(name = "location", columnDefinition = "geometry(Point, 4326)")
    private Point location;  // PostGIS geometry for geofencing (50m radius)

    @Column(name = "external_id")
    private String externalId;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    public CheckpointEntity() {
        this.id = UUID.randomUUID();
        this.createdAt = Instant.now();
        this.updatedAt = Instant.now();
    }

    @PreUpdate
    protected void onUpdate() {
        this.updatedAt = Instant.now();
    }

}
