package nl.vlettenrally.service.impl;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import nl.vlettenrally.data.domain.Game;
import nl.vlettenrally.data.repository.GameRepository;
import nl.vlettenrally.service.GameService;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Implementation of GameService
 */
@ApplicationScoped
public class GameServiceImpl implements GameService {

    @Inject
    GameRepository gameRepository;

    @Override
    public Optional<Game> findById(UUID id) {
        return gameRepository.findById(id);
    }

    @Override
    public List<Game> findAll() {
        return gameRepository.findAll();
    }

    @Override
    public List<Game> findActiveGames() {
        return gameRepository.findActiveGames();
    }

    @Override
    public List<Game> findByStatus(Game.GameStatus status) {
        return gameRepository.findByStatus(status);
    }

    @Override
    public Game save(Game game) {
        return gameRepository.save(game);
    }

    @Override
    public void delete(UUID id) {
        gameRepository.delete(id);
    }

    @Override
    public long countGames() {
        return gameRepository.count();
    }
}
