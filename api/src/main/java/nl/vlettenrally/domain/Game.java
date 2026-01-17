package nl.vlettenrally.domain;

import java.time.Instant;
import java.util.UUID;

/**
 * Game entity representing a rally event
 */
public class Game {
    private UUID id;
    private String name;
    private String description;
    private Instant startTime;
    private Instant endTime;
    private Instant startingAreaActiveTime;
    private Instant startSignalTime;
    private GameStatus status;
    private Instant createdAt;
    private Instant updatedAt;

    public enum GameStatus {
        PLANNED,
        ACTIVE,
        COMPLETED
    }

    public Game() {
        this.id = UUID.randomUUID();
        this.createdAt = Instant.now();
        this.updatedAt = Instant.now();
    }

    public Game(UUID id, String name, String description, Instant startTime, Instant endTime, GameStatus status) {
        this.id = id;
        this.name = name;
        this.description = description;
        this.startTime = startTime;
        this.endTime = endTime;
        this.status = status;
        this.createdAt = Instant.now();
        this.updatedAt = Instant.now();
    }

    // Getters and Setters
    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    public Instant getStartTime() { return startTime; }
    public void setStartTime(Instant startTime) { this.startTime = startTime; }

    public Instant getEndTime() { return endTime; }
    public void setEndTime(Instant endTime) { this.endTime = endTime; }

    public Instant getStartingAreaActiveTime() { return startingAreaActiveTime; }
    public void setStartingAreaActiveTime(Instant startingAreaActiveTime) { this.startingAreaActiveTime = startingAreaActiveTime; }

    public Instant getStartSignalTime() { return startSignalTime; }
    public void setStartSignalTime(Instant startSignalTime) { this.startSignalTime = startSignalTime; }

    public GameStatus getStatus() { return status; }
    public void setStatus(GameStatus status) { this.status = status; }

    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }

    public Instant getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }
}
