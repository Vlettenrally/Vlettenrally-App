package nl.vlettenrally.data.entity.jpa;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

@Getter
@Setter
@Entity
@Table(name = "admin_notifications", indexes = {
    @Index(name = "idx_admin_notifications_game_id", columnList = "game_id"),
    @Index(name = "idx_admin_notifications_game_created_at", columnList = "game_id, created_at"),
    @Index(name = "idx_admin_notifications_game_read", columnList = "game_id, read")
})
public class AdminNotificationEntity {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "game_id", nullable = false)
    private GameEntity game;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "team_id")
    private TeamEntity team;

    @Column(name = "source", nullable = false, length = 50)
    private String source;

    @Column(name = "content", nullable = false, columnDefinition = "TEXT")
    private String content;

    @Column(name = "metadata_json", columnDefinition = "TEXT")
    private String metadataJson;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "read", nullable = false)
    private boolean read = false;

    @Column(name = "read_at")
    private Instant readAt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "read_by_admin_id")
    private AdminEntity readByAdmin;

    public AdminNotificationEntity() {
        this.id = UUID.randomUUID();
        this.createdAt = Instant.now();
    }
}
