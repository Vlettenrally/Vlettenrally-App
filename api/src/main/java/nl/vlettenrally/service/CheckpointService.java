package nl.vlettenrally.service;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import nl.vlettenrally.data.domain.Checkpoint;
import nl.vlettenrally.data.dto.game.CheckpointRequest;
import nl.vlettenrally.data.dto.game.CopyCheckpointsResult;

/**
 * Service interface for Checkpoint operations
 */
public interface CheckpointService {

    Optional<Checkpoint> findById(UUID id);
    
    List<Checkpoint> findAll();
    
    List<Checkpoint> findByGameId(UUID gameId);

    Optional<Checkpoint> findByGameIdAndExternalId(UUID gameId, String externalId);

    Checkpoint save(Checkpoint checkpoint);

    /**
     * Create a checkpoint for a game from a request, choosing the zone type
     * (circle or polygon) based on the request contents.
     */
    Checkpoint create(UUID gameId, CheckpointRequest request);

    /**
     * Update a checkpoint from a request, switching zone type (circle ↔ polygon)
     * when the request asks for a different type than the existing checkpoint.
     *
     * @return the saved checkpoint, or empty if no checkpoint exists with the given id
     */
    Optional<Checkpoint> update(UUID checkpointId, CheckpointRequest request);

    /**
     * Copy all zones from {@code sourceGameId} into {@code targetGameId}. Opening
     * and closing times are shifted into the target game's timeframe; the hidden
     * flag is preserved; external ids are dropped (they are tracker-sync keys that
     * must stay unique per game). Source zones whose name already exists in the
     * target are skipped, so re-copying is idempotent.
     *
     * @return a summary of how many zones were copied and which were skipped
     */
    CopyCheckpointsResult copyCheckpoints(UUID targetGameId, UUID sourceGameId);

    void delete(UUID id);
}
