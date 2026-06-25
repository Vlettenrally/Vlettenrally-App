package nl.vlettenrally.data.entity.jpa;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Getter;
import lombok.Setter;

import java.util.UUID;

/**
 * One tracker ID in a game's pool of available physical trackers. Membership only;
 * the team it is assigned to (if any) lives on {@code teams.tracker_id}.
 */
@Setter
@Getter
@Entity
@Table(
    name = "game_trackers",
    uniqueConstraints = @UniqueConstraint(name = "uq_game_trackers_game_tracker", columnNames = {"game_id", "tracker_id"}),
    indexes = @Index(name = "idx_game_trackers_game", columnList = "game_id")
)
public class GameTrackerEntity {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id = UUID.randomUUID();

    @Column(name = "game_id", nullable = false)
    private UUID gameId;

    @Column(name = "tracker_id", nullable = false, length = 64)
    private String trackerId;
}
