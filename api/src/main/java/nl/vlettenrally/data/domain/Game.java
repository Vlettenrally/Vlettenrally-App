package nl.vlettenrally.data.domain;

import lombok.Getter;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

/**
 * Game entity representing a rally event
 */
@Setter
@Getter
public class Game {
    // Getters and Setters
    private UUID id;
    private String name;
    private String description;
    private String participantInfo;
    private Instant startTime;
    private Instant endTime;
    private Instant checkInStartTime;
    private Instant startSignalTime;
    private GameStatus status;
    private Instant createdAt;
    private Instant updatedAt;

    // External event integration
    // External event integration
    private Integer externalEventId;
    private String externalEventName;

    // Location tracking configuration
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

}
