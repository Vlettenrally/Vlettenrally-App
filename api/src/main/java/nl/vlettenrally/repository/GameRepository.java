package nl.vlettenrally.repository;

import nl.vlettenrally.domain.Game;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Repository interface for Game entities
 */
public interface GameRepository {
    
    Optional<Game> findById(UUID id);
    
    List<Game> findAll();
    
    List<Game> findByStatus(Game.GameStatus status);
    
    List<Game> findActiveGames();
    
    Game save(Game game);
    
    void delete(UUID id);
}
