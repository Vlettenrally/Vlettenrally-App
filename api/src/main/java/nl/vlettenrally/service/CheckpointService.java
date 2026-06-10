package nl.vlettenrally.service;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import nl.vlettenrally.data.domain.Checkpoint;
import nl.vlettenrally.data.dto.game.CheckpointRequest;

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

    void delete(UUID id);
}
