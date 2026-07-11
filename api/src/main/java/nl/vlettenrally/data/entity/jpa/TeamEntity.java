package nl.vlettenrally.data.entity.jpa;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * JPA entity for Team
 */
@Setter
@Getter
@Entity
@Table(name = "teams", indexes = {
    @Index(name = "idx_teams_game_id", columnList = "game_id"),
    @Index(name = "idx_teams_device_id", columnList = "device_id"),
    @Index(name = "idx_teams_checked_in", columnList = "checked_in")
})
public class TeamEntity {

    // Getters and Setters
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

    @Column(name = "main_device_id")
    private UUID mainDeviceId;

    @Column(name = "checked_in", nullable = false)
    private boolean checkedIn = false;

    @Column(name = "checked_in_digitally", nullable = false)
    private boolean checkedInDigitally = false;

    @Column(name = "checked_in_at")
    private Instant checkedInAt;

    @Column(name = "finished", nullable = false)
    private boolean finished = false;

    @Column(name = "finished_at")
    private Instant finishedAt;

    @Column(name = "traccar_enabled", nullable = false)
    private boolean traccarEnabled = false;

    @Column(name = "traccar_identifier", length = 64)
    private String traccarIdentifier;

    // Physical GPS tracker ID from the external provider, assigned in bulk and
    // exported back to them. Unrelated to traccarIdentifier above.
    @Column(name = "tracker_id", length = 64)
    private String trackerId;

    @Version
    @Column(name = "version", nullable = false)
    private long version;

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

}
