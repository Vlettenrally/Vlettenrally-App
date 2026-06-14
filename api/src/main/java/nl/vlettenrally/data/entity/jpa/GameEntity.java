package nl.vlettenrally.data.entity.jpa;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

/**
 * JPA entity for Game
 */
@Setter
@Getter
@Entity
@Table(name = "games", indexes = {
    @Index(name = "idx_games_status", columnList = "status"),
    @Index(name = "idx_games_start_time", columnList = "start_time")
})
public class GameEntity {

    // Getters and Setters
    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "name", nullable = false)
    private String name;

    @Column(name = "description", columnDefinition = "TEXT")
    private String description;

    @Column(name = "participant_info", columnDefinition = "TEXT")
    private String participantInfo;

    @Column(name = "start_time")
    private Instant startTime;

    @Column(name = "end_time")
    private Instant endTime;

    @Column(name = "check_in_start_time")
    private Instant checkInStartTime;

    @Column(name = "start_signal_time")
    private Instant startSignalTime;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private GameStatus status = GameStatus.PLANNED;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    // External event integration
    // External event integration
    @Column(name = "external_event_id")
    private Integer externalEventId;

    @Column(name = "external_event_name")
    private String externalEventName;

    // Location tracking configuration
    // Location tracking configuration
    @Column(name = "location_endpoint_url", columnDefinition = "TEXT")
    private String locationEndpointUrl;

    @Column(name = "location_polling_frequency", nullable = false)
    private Integer locationPollingFrequency = 15;

    @Column(name = "location_endpoint_active", nullable = false)
    private Boolean locationEndpointActive = false;

    // Whether the final ranking has been revealed to players (set by an admin at
    // the award ceremony). Gates all player-facing ranking visibility.
    @Column(name = "rankings_published", nullable = false)
    private Boolean rankingsPublished = false;

    public enum GameStatus {
        PLANNED,
        ACTIVE,
        COMPLETED
    }

    public GameEntity() {
        this.id = UUID.randomUUID();
        this.createdAt = Instant.now();
        this.updatedAt = Instant.now();
    }

    @PreUpdate
    protected void onUpdate() {
        this.updatedAt = Instant.now();
    }

}
