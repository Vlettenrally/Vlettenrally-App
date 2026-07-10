package nl.vlettenrally.service;

import io.quarkus.logging.Log;
import io.quarkus.scheduler.Scheduled;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import nl.vlettenrally.data.domain.Game;
import nl.vlettenrally.websockets.WebSocketEventTypes;
import nl.vlettenrally.websockets.WebSocketPublisher;

import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * Scheduler for automatic game status transitions.
 * <p>
 * Transitions:
 * - PLANNED → ACTIVE: When startTime is reached
 * - ACTIVE → COMPLETED: When endTime is reached
 * <p>
 * Runs on a single instance at a time — leader election via
 * {@link PollingLeader} (Valkey-backed lease, shared with the other
 * background schedulers).
 */
@ApplicationScoped
public class GameStatusScheduler {

    @Inject
    GameService gameService;

    @Inject
    WebSocketPublisher webSocketPublisher;

    @Inject
    AutomationEngine automationEngine;

    @Inject
    LocationIngestService locationIngestService;

    @Inject
    PollingLeader pollingLeader;

    /**
     * Check for game status transitions on the automation tick interval
     * ({@code vlettenrally.automation.tick-interval}, default 5s) so auto
     * start/finish and GAME_STATUS_CHANGE automations are not delayed.
     */
    @Scheduled(every = "{vlettenrally.automation.tick-interval}")
    @Transactional
    void checkGameStatusTransitions() {
        if (!pollingLeader.tryAcquireOrRenew()) {
            return;
        }

        Instant now = Instant.now();

        // Check PLANNED games for transition to ACTIVE
        List<Game> plannedGames = gameService.findByStatus(Game.GameStatus.PLANNED);
        for (Game game : plannedGames) {
            if (game.getStartTime() != null && !now.isBefore(game.getStartTime())) {
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

        automationEngine.onGameStatusChange(game, Game.GameStatus.PLANNED, Game.GameStatus.ACTIVE);

        // Fire automations for teams already sitting inside a zone at kickoff.
        locationIngestService.evaluateCurrentPositions(game.getId());
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


        Log.info("Game '" + game.getName() + "' transitioned to COMPLETED");

        automationEngine.onGameStatusChange(game, Game.GameStatus.ACTIVE, Game.GameStatus.COMPLETED);
    }
}
