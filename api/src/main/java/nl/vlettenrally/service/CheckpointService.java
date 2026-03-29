package nl.vlettenrally.service;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import nl.vlettenrally.data.domain.Checkpoint;

/**
 * Service interface for Checkpoint operations
 */
public interface CheckpointService {
    
    Optional<Checkpoint> findById(UUID id);
    
    List<Checkpoint> findAll();
    
    List<Checkpoint> findByGameId(UUID gameId);

    Optional<Checkpoint> findByGameIdAndExternalId(UUID gameId, String externalId);

    Checkpoint save(Checkpoint checkpoint);
    
    void delete(UUID id);
}
