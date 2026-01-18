package nl.vlettenrally.data.repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import nl.vlettenrally.data.domain.Checkpoint;

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
