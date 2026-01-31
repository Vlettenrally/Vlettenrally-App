package nl.vlettenrally.data.entity.jpa;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * JPA entity for Team
 */
@Entity
@Table(name = "teams", indexes = {
    @Index(name = "idx_teams_game_id", columnList = "game_id"),
    @Index(name = "idx_teams_device_id", columnList = "device_id"),
    @Index(name = "idx_teams_boat_number", columnList = "boat_number"),
    @Index(name = "idx_teams_checked_in", columnList = "checked_in")
})
public class TeamEntity {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "game_id", nullable = false)
    private GameEntity game;

    @Column(name = "name", nullable = false)
    private String name;

    @Column(name = "description", columnDefinition = "TEXT")
    private String description;

    @Column(name = "boat_number", length = 50)
    private String boatNumber;

    @Column(name = "external_id")
    private String externalId;

    @ElementCollection
    @CollectionTable(
        name = "team_participants",
        joinColumns = @JoinColumn(name = "team_id")
    )
    @Column(name = "participant_name")
    @OrderColumn(name = "participant_order")
    private List<String> participants = new ArrayList<>();

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "device_id")
    private DeviceRegistrationEntity device;

    @Column(name = "checked_in", nullable = false)
    private boolean checkedIn = false;

    @Column(name = "total_points", nullable = false)
    private int totalPoints = 0;

    /**
     * Unidirectional ManyToMany self-reference for rivals.
     * Each team manages their own list of rivals independently.
     */
    @ManyToMany(fetch = FetchType.LAZY)
    @JoinTable(
        name = "team_rivals",
        joinColumns = @JoinColumn(name = "team_id"),
        inverseJoinColumns = @JoinColumn(name = "rival_team_id")
    )
    private Set<TeamEntity> rivals = new HashSet<>();

    @Column(name = "notes", columnDefinition = "TEXT")
    private String notes;

    @ElementCollection
    @CollectionTable(
        name = "team_emergency_phones",
        joinColumns = @JoinColumn(name = "team_id")
    )
    @Column(name = "phone_number", nullable = false, length = 50)
    @OrderColumn(name = "phone_order")
    private List<String> emergencyPhoneNumbers = new ArrayList<>();

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    public TeamEntity() {
        this.id = UUID.randomUUID();
        this.createdAt = Instant.now();
        this.updatedAt = Instant.now();
    }

    @PreUpdate
    protected void onUpdate() {
        this.updatedAt = Instant.now();
    }

    /**
     * Add a rival team. Validates that a team cannot be its own rival.
     * @throws IllegalArgumentException if trying to add self as rival
     */
    public void addRival(TeamEntity rival) {
        if (rival != null && rival.getId() != null && rival.getId().equals(this.id)) {
            throw new IllegalArgumentException("A team cannot be its own rival");
        }
        this.rivals.add(rival);
    }

    /**
     * Remove a rival team.
     */
    public void removeRival(TeamEntity rival) {
        this.rivals.remove(rival);
    }

    // Getters and Setters
    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }

    public GameEntity getGame() { return game; }
    public void setGame(GameEntity game) { this.game = game; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    public String getBoatNumber() { return boatNumber; }
    public void setBoatNumber(String boatNumber) { this.boatNumber = boatNumber; }

    public String getExternalId() { return externalId; }
    public void setExternalId(String externalId) { this.externalId = externalId; }

    public List<String> getParticipants() { return participants; }
    public void setParticipants(List<String> participants) { this.participants = participants; }

    public DeviceRegistrationEntity getDevice() { return device; }
    public void setDevice(DeviceRegistrationEntity device) { this.device = device; }

    public boolean isCheckedIn() { return checkedIn; }
    public void setCheckedIn(boolean checkedIn) { this.checkedIn = checkedIn; }

    public int getTotalPoints() { return totalPoints; }
    public void setTotalPoints(int totalPoints) { this.totalPoints = totalPoints; }

    public Set<TeamEntity> getRivals() { return rivals; }
    public void setRivals(Set<TeamEntity> rivals) { this.rivals = rivals; }

    public String getNotes() { return notes; }
    public void setNotes(String notes) { this.notes = notes; }

    public List<String> getEmergencyPhoneNumbers() { return emergencyPhoneNumbers; }
    public void setEmergencyPhoneNumbers(List<String> emergencyPhoneNumbers) { this.emergencyPhoneNumbers = emergencyPhoneNumbers; }

    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }

    public Instant getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }
}
