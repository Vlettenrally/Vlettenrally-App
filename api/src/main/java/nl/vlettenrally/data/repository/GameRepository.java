package nl.vlettenrally.data.repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import nl.vlettenrally.data.domain.Game;

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
