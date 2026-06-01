package nl.vlettenrally.service;

import io.quarkus.logging.Log;
import io.quarkus.scheduler.Scheduled;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import nl.vlettenrally.data.domain.Game;
import nl.vlettenrally.websockets.WebSocketEventTypes;
import nl.vlettenrally.websockets.WebSocketPublisher;
import nl.vlettenrally.websockets.dto.WebSocketMessageType;

import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * Scheduler for automatic game status transitions.
 * 
 * Transitions:
 * - PLANNED → ACTIVE: When startSignalTime is reached
 * - ACTIVE → COMPLETED: When endTime is reached
 */
@ApplicationScoped
public class GameStatusScheduler {

    @Inject
    GameService gameService;

    @Inject
    WebSocketPublisher webSocketPublisher;

    @Inject
    RankingService rankingService;

    /**
     * Check for game status transitions every 30 seconds
     */
    @Scheduled(every = "30s")
    @Transactional
    void checkGameStatusTransitions() {
        Instant now = Instant.now();
        
        // Check PLANNED games for transition to ACTIVE
        List<Game> plannedGames = gameService.findByStatus(Game.GameStatus.PLANNED);
        for (Game game : plannedGames) {
            if (game.getStartSignalTime() != null && !now.isBefore(game.getStartSignalTime())) {
                transitionToActive(game);
            }
        }
        
        // Check ACTIVE games for transition to COMPLETED
        List<Game> activeGames = gameService.findByStatus(Game.GameStatus.ACTIVE);
        for (Game game : activeGames) {
            if (game.getEndTime() != null && !now.isBefore(game.getEndTime())) {
                transitionToCompleted(game);
            }
        }
    }
    
    private void transitionToActive(Game game) {
        game.setStatus(Game.GameStatus.ACTIVE);
        gameService.save(game);
        
        // Notify all connected clients
        webSocketPublisher.broadcastToGame(
            game.getId(),
            WebSocketEventTypes.GAME_STATUS,
            Map.of(
                "gameId", game.getId(),
                "status", "active",
                "previousStatus", "planned"
            )
        );
        
        Log.info("Game '" + game.getName() + "' transitioned to ACTIVE");
    }
    
    private void transitionToCompleted(Game game) {
        game.setStatus(Game.GameStatus.COMPLETED);
        gameService.save(game);
        
        // Notify all connected clients
        webSocketPublisher.broadcastToGame(
            game.getId(),
            WebSocketEventTypes.GAME_STATUS,
            Map.of(
                "gameId", game.getId(),
                "status", "completed",
                "previousStatus", "active"
            )
        );

        // Scores become public on completion — push the full ranking so already
        // connected clients see the final standings without re-fetching.
        webSocketPublisher.broadcastToGame(
            game.getId(),
            WebSocketMessageType.RANKING_UPDATE.getValue(),
            rankingService.buildRanking(game.getId())
        );

        Log.info("Game '" + game.getName() + "' transitioned to COMPLETED");
    }
}
