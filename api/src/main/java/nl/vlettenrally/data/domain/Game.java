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
    /** Legendstracking event base URL (e.g. https://host/api/event/3265). No trailing slash, no suffix. */
    private String locationEndpointUrl;
    private Integer locationPollingFrequency;
    private Boolean locationEndpointActive;

    /** Whether the final ranking has been revealed to players (admin-triggered at the ceremony). */
    private Boolean rankingsPublished;

    public enum GameStatus {
        PLANNED,
        ACTIVE,
        COMPLETED
    }

    public Game() {
        this.id = UUID.randomUUID();
        this.status = GameStatus.PLANNED;
        this.locationPollingFrequency = 30; // Matches Legendstracking 30 s cache
        this.locationEndpointActive = false;
        this.rankingsPublished = false;
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
