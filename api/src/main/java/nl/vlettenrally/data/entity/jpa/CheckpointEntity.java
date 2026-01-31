package nl.vlettenrally.data.entity.jpa;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

/**
 * JPA entity for Checkpoint
 */
@Entity
@Table(name = "checkpoints", indexes = {
    @Index(name = "idx_checkpoints_game_id", columnList = "game_id"),
    @Index(name = "idx_checkpoints_opening_time", columnList = "opening_time")
})
public class CheckpointEntity {

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

    // Getters and Setters
    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }

    public GameEntity getGame() { return game; }
    public void setGame(GameEntity game) { this.game = game; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public Instant getOpeningTime() { return openingTime; }
    public void setOpeningTime(Instant openingTime) { this.openingTime = openingTime; }

    public Instant getClosingTime() { return closingTime; }
    public void setClosingTime(Instant closingTime) { this.closingTime = closingTime; }

    public int getPoints() { return points; }
    public void setPoints(int points) { this.points = points; }

    public Double getLatitude() { return latitude; }
    public void setLatitude(Double latitude) { this.latitude = latitude; }

    public Double getLongitude() { return longitude; }
    public void setLongitude(Double longitude) { this.longitude = longitude; }

    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }

    public Instant getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }
}
