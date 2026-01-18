package nl.vlettenrally.data.domain;

import java.time.Instant;
import java.util.UUID;

/**
 * Checkpoint entity representing a rally checkpoint
 */
public class Checkpoint {
    private UUID id;
    private UUID gameId;
    private String name;
    private Instant openingTime;
    private Instant closingTime;
    private int points;
    private Double latitude;
    private Double longitude;
    private Instant createdAt;
    private Instant updatedAt;

    public Checkpoint() {
        this.id = UUID.randomUUID();
        this.createdAt = Instant.now();
        this.updatedAt = Instant.now();
    }

    public Checkpoint(UUID id, UUID gameId, String name, Instant openingTime, int points) {
        this();
        this.id = id;
        this.gameId = gameId;
        this.name = name;
        this.openingTime = openingTime;
        this.points = points;
    }

    // Getters and Setters
    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }

    public UUID getGameId() { return gameId; }
    public void setGameId(UUID gameId) { this.gameId = gameId; }

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
