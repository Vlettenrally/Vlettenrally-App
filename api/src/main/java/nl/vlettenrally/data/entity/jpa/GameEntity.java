package nl.vlettenrally.data.entity.jpa;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

/**
 * JPA entity for Game
 */
@Entity
@Table(name = "games", indexes = {
    @Index(name = "idx_games_status", columnList = "status"),
    @Index(name = "idx_games_start_time", columnList = "start_time")
})
public class GameEntity {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "name", nullable = false)
    private String name;

    @Column(name = "description", columnDefinition = "TEXT")
    private String description;

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
    @Column(name = "external_event_id")
    private Integer externalEventId;

    @Column(name = "external_event_name")
    private String externalEventName;

    // Location tracking configuration
    @Column(name = "location_endpoint_url", columnDefinition = "TEXT")
    private String locationEndpointUrl;

    @Column(name = "location_polling_frequency", nullable = false)
    private Integer locationPollingFrequency = 15;

    @Column(name = "location_endpoint_active", nullable = false)
    private Boolean locationEndpointActive = false;

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

    public Instant getCheckInStartTime() { return checkInStartTime; }
    public void setCheckInStartTime(Instant checkInStartTime) { this.checkInStartTime = checkInStartTime; }

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
