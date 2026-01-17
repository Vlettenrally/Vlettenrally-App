package nl.vlettenrally.service.impl;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import nl.vlettenrally.domain.Checkpoint;
import nl.vlettenrally.repository.CheckpointRepository;
import nl.vlettenrally.service.CheckpointService;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Implementation of CheckpointService
 */
@ApplicationScoped
public class CheckpointServiceImpl implements CheckpointService {

    @Inject
    CheckpointRepository checkpointRepository;

    @Override
    public Optional<Checkpoint> findById(UUID id) {
        return checkpointRepository.findById(id);
    }

    @Override
    public List<Checkpoint> findAll() {
        return checkpointRepository.findAll();
    }

    @Override
    public List<Checkpoint> findByGameId(UUID gameId) {
        return checkpointRepository.findByGameId(gameId);
    }

    @Override
    public Checkpoint save(Checkpoint checkpoint) {
        return checkpointRepository.save(checkpoint);
    }

    @Override
    public void delete(UUID id) {
        checkpointRepository.delete(id);
    }
}
