package nl.vlettenrally.data.repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import nl.vlettenrally.data.domain.Achievement;

/**
 * Repository interface for Achievement entities
 */
public interface AchievementRepository {

    Optional<Achievement> findById(UUID id);

    List<Achievement> findAll();

    List<Achievement> findByTeamId(UUID teamId);

    List<Achievement> findByCheckpointId(UUID checkpointId);

    Optional<Achievement> findByTeamIdAndCheckpointId(UUID teamId, UUID checkpointId);

    Achievement save(Achievement achievement);

    void delete(UUID id);

    /**
     * Delete all REJECTED achievements whose verifiedAt is older than the cutoff.
     * Returns the number of rows deleted.
     */
    int deleteRejectedOlderThan(Instant cutoff);
}
