package nl.vlettenrally.repository;

import nl.vlettenrally.domain.Checkpoint;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Repository interface for Checkpoint entities
 */
public interface CheckpointRepository {
    
    Optional<Checkpoint> findById(UUID id);
    
    List<Checkpoint> findAll();
    
    List<Checkpoint> findByGameId(UUID gameId);
    
    Checkpoint save(Checkpoint checkpoint);
    
    void delete(UUID id);
}
