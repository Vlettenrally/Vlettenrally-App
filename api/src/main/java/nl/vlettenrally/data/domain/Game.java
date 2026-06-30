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
    /** Minutes before {@link #startSignalTime} to begin polling/ingesting locations. 0 = off. */
    private Integer locationPollingLeadMinutes;

    /** Whether the final ranking has been revealed to players (admin-triggered at the ceremony). */
    private Boolean rankingsPublished;

    /** Whether the ranking is visible on the public scoreboard API (independent of player-app visibility). */
    private Boolean publicRankingsEnabled;

    // Per-game location-ingest guard overrides. Null = inherit the global default from
    // application.properties (vlettenrally.location.*); a non-null value overrides that
    // guard for this game only. See LocationGuardConfig for resolution + caching.
    /** Hard teleport ceiling (km/h): any implied speed above this is dropped. Null = global. */
    private Double locMaxSpeedKmh;
    /** Soft ceiling (km/h) for the direction-gated spike rule. Null = global. */
    private Double locBoatMaxSpeedKmh;
    /** A turn sharper than this (degrees) is a spike candidate. Null = global. */
    private Double locTurnAngleThresholdDegrees;
    /** Legs shorter than this (meters) skip the turn test. Null = global. */
    private Double locMinBearingSegmentMeters;
    /** Drop fixes whose reported accuracy exceeds this (meters); null bypasses. Null = global. */
    private Double locMaxAccuracyMeters;
    /** Gaps longer than this (seconds) bypass the boat-speed/turn rules. Null = global. */
    private Long locStaleFixSeconds;

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
        this.locationPollingLeadMinutes = 30; // Start ingesting 30 min before the start signal

        this.rankingsPublished = false;
        this.publicRankingsEnabled = false;
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
