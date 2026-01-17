package nl.vlettenrally.service;

import nl.vlettenrally.domain.Checkpoint;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Service interface for Checkpoint operations
 */
public interface CheckpointService {
    
    Optional<Checkpoint> findById(UUID id);
    
    List<Checkpoint> findAll();
    
    List<Checkpoint> findByGameId(UUID gameId);
    
    Checkpoint save(Checkpoint checkpoint);
    
    void delete(UUID id);
}
