package nl.vlettenrally.data.entity.jpa;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

/**
 * JPA entity for one emergency-stop period of a game. Only {@code resumedAt} is ever
 * updated (when the game resumes); everything else is written once at pause time.
 * The partial unique index {@code uq_gpp_open} (see V82) enforces at most one open
 * period per game at the database level.
 */
@Setter
@Getter
@Entity
@Table(name = "game_pause_periods", indexes = {
    @Index(name = "idx_gpp_game", columnList = "game_id, paused_at")
})
public class GamePausePeriodEntity {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "game_id", nullable = false, updatable = false)
    private UUID gameId;

    @Column(name = "paused_at", nullable = false, updatable = false)
    private Instant pausedAt;

    @Column(name = "resumed_at")
    private Instant resumedAt;

    @Column(name = "reason", columnDefinition = "TEXT", updatable = false)
    private String reason;

    @Column(name = "paused_by", updatable = false)
    private UUID pausedBy;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;
}
