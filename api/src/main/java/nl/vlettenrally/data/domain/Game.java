package nl.vlettenrally.data.domain;

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
    
    // External event integration
    private Integer externalEventId;
    private String externalEventName;
    
    // Location tracking configuration
    private String locationEndpointUrl;
    private Integer locationPollingFrequency;
    private Boolean locationEndpointActive;

    public enum GameStatus {
        PLANNED,
        ACTIVE,
        COMPLETED
    }

    public Game() {
        this.id = UUID.randomUUID();
        this.status = GameStatus.PLANNED;
        this.locationPollingFrequency = 15; // Default to 15 seconds
        this.locationEndpointActive = false;
        this.createdAt = Instant.now();
        this.updatedAt = Instant.now();
    }

    public Game(UUID id, String name, String description, Instant startTime, Instant endTime, GameStatus status) {
        this();
        this.id = id;
        this.name = name;
        this.description = description;
        this.startTime = startTime;
        this.endTime = endTime;
        this.status = status;
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

    // External event integration
    public Integer getExternalEventId() { return externalEventId; }
    public void setExternalEventId(Integer externalEventId) { this.externalEventId = externalEventId; }

    public String getExternalEventName() { return externalEventName; }
    public void setExternalEventName(String externalEventName) { this.externalEventName = externalEventName; }

    // Location tracking configuration
    public String getLocationEndpointUrl() { return locationEndpointUrl; }
    public void setLocationEndpointUrl(String locationEndpointUrl) { this.locationEndpointUrl = locationEndpointUrl; }

    public Integer getLocationPollingFrequency() { return locationPollingFrequency; }
    public void setLocationPollingFrequency(Integer locationPollingFrequency) { this.locationPollingFrequency = locationPollingFrequency; }

    public Boolean getLocationEndpointActive() { return locationEndpointActive; }
    public void setLocationEndpointActive(Boolean locationEndpointActive) { this.locationEndpointActive = locationEndpointActive; }
}
