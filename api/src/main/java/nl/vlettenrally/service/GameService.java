package nl.vlettenrally.service;

import nl.vlettenrally.domain.Game;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Service interface for Game operations
 */
public interface GameService {
    
    Optional<Game> findById(UUID id);
    
    List<Game> findAll();
    
    List<Game> findActiveGames();
    
    List<Game> findByStatus(Game.GameStatus status);
    
    Game save(Game game);
    
    void delete(UUID id);
}
