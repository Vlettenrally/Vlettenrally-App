package nl.vlettenrally.data.entity.jpa;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import nl.vlettenrally.data.enums.CheckInMethod;
import org.hibernate.annotations.BatchSize;

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

    /**
     * Batch size for this entity's lazy collections.
     * <p>
     * Every list-of-teams read maps each row through {@code TeamMapper.toDomain}, which touches
     * {@link #participants}, {@link #emergencyPhoneNumbers} and {@link #rivals}. Without batching
     * that is three SELECTs per team — ~750 read-replica round trips for a full 250-team field,
     * which is what made {@code GET /games/{gameId}/teams} take close to a second. Since the whole
     * page of teams is loaded in one query, all their collections sit uninitialized in the same
     * persistence context and Hibernate can fetch them a batch at a time instead.
     * <p>
     * A {@code LEFT JOIN FETCH} is not an option here: {@code participants} and
     * {@code emergencyPhoneNumbers} are both {@code List} {@code @ElementCollection}s with an
     * {@code @OrderColumn}, so fetching both in one query throws {@code MultipleBagFetchException}.
     */
    private static final int COLLECTION_BATCH_SIZE = 100;

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
    @BatchSize(size = COLLECTION_BATCH_SIZE)
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

    @Enumerated(EnumType.STRING)
    @Column(name = "check_in_method", length = 10)
    private CheckInMethod checkInMethod;

    @Column(name = "finished", nullable = false)
    private boolean finished = false;

    @Column(name = "finished_at")
    private Instant finishedAt;

    @Column(name = "free_class", nullable = false)
    private boolean freeClass = false;

    @Column(name = "disqualified", nullable = false)
    private boolean disqualified = false;

    @Column(name = "disqualified_at")
    private Instant disqualifiedAt;

    @Column(name = "traccar_enabled", nullable = false)
    private boolean traccarEnabled = false;

    @Column(name = "traccar_identifier", length = 64)
    private String traccarIdentifier;

    // Physical GPS tracker ID from the external provider, assigned in bulk and
    // exported back to them. Unrelated to traccarIdentifier above.
    @Column(name = "tracker_id", length = 64)
    private String trackerId;

    // When trackerId was last (re)assigned; lower bound for location backfill.
    @Column(name = "tracker_assigned_at")
    private Instant trackerAssignedAt;

    // Whether the team has physically returned its tracker at the end of the game.
    // Independent of trackerId/trackerAssignedAt — returning a tracker does not
    // clear its assignment.
    @Column(name = "tracker_handed_in", nullable = false)
    private boolean trackerHandedIn = false;

    @Column(name = "tracker_handed_in_at")
    private Instant trackerHandedInAt;

    @Version
    @Column(name = "version", nullable = false)
    private long version;

    /**
     * Unidirectional ManyToMany self-reference for rivals.
     * Each team manages their own list of rivals independently.
     */
    @ManyToMany(fetch = FetchType.LAZY)
    @BatchSize(size = COLLECTION_BATCH_SIZE)
    @JoinTable(
        name = "team_rivals",
        joinColumns = @JoinColumn(name = "team_id"),
        inverseJoinColumns = @JoinColumn(name = "rival_team_id")
    )
    private Set<TeamEntity> rivals = new HashSet<>();

    @Column(name = "notes", columnDefinition = "TEXT")
    private String notes;

    @ElementCollection
    @BatchSize(size = COLLECTION_BATCH_SIZE)
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
