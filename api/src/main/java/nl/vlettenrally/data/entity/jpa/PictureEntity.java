package nl.vlettenrally.data.entity.jpa;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import nl.vlettenrally.data.enums.PictureStatus;

import java.time.Instant;
import java.util.UUID;

/**
 * JPA entity for a team-uploaded picture.
 * <p>
 * Flow:
 * 1. Team requests an upload → a PENDING row is reserved with a unique object key
 *    (uploadedAt is null until the browser PUTs the file and confirms).
 * 2. Team confirms → uploadedAt is set; the row becomes eligible for moderation.
 * 3. Admin approves (APPROVED) or rejects (REJECTED). Rejected objects are retained.
 */
@Setter
@Getter
@Entity
@Table(name = "pictures", indexes = {
    @Index(name = "idx_pictures_team_id", columnList = "team_id"),
    @Index(name = "idx_pictures_game_id", columnList = "game_id"),
    @Index(name = "idx_pictures_moderation", columnList = "status, uploaded_at"),
    // Keyset paging of the picture lists; see V81__index_pictures_for_paging.sql for the
    // descending column order Flyway actually creates.
    @Index(name = "idx_pictures_game_created", columnList = "game_id, created_at, id"),
    @Index(name = "idx_pictures_game_status_uploaded", columnList = "game_id, status, uploaded_at, id")
})
public class PictureEntity {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "team_id", nullable = true)
    private TeamEntity team;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "game_id", nullable = false)
    private GameEntity game;

    @Column(name = "object_key", nullable = false, unique = true, columnDefinition = "TEXT")
    private String objectKey;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private PictureStatus status = PictureStatus.PENDING;

    @Column(name = "content_type", length = 100)
    private String contentType;

    @Column(name = "size_bytes")
    private Long sizeBytes;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Column(name = "uploaded_at")
    private Instant uploadedAt;

    @Column(name = "moderated_at")
    private Instant moderatedAt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "moderated_by")
    private AdminEntity moderatedBy;

    @Column(name = "notes", columnDefinition = "TEXT")
    private String notes;

    @Version
    @Column(name = "version", nullable = false)
    private long version;

    public PictureEntity() {
        this.id = UUID.randomUUID();
        this.createdAt = Instant.now();
        this.updatedAt = Instant.now();
    }

    @PreUpdate
    protected void onUpdate() {
        this.updatedAt = Instant.now();
    }
}
