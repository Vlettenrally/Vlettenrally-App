package nl.vlettenrally.service;

import io.quarkus.logging.Log;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Status;
import jakarta.transaction.Synchronization;
import jakarta.transaction.Transactional;
import jakarta.transaction.TransactionSynchronizationRegistry;
import jakarta.ws.rs.core.Response;
import nl.vlettenrally.data.domain.Game;
import nl.vlettenrally.data.domain.GamePausePeriod;
import nl.vlettenrally.data.repository.GamePausePeriodRepository;
import nl.vlettenrally.websockets.WebSocketEventTypes;
import nl.vlettenrally.websockets.WebSocketPublisher;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Emergency stop: freezes a running game's competition without stopping its tracking.
 * <p>
 * While a game is PAUSED, location ingest carries on exactly as before — fixes are stored
 * and broadcast to the admin map, which is the whole point when the reason for the stop is
 * a boat in trouble — but no distance points accrue, no checkpoint passes are detected and
 * no scheduled automations fire. Picture uploads, speed alerts and rival selection stay
 * open. The game's {@code endTime} is deliberately <em>not</em> extended: the rally still
 * ends when it was planned to, and an admin who wants to give the time back edits the end
 * time explicitly.
 * <p>
 * Each stop is recorded as a {@link GamePausePeriod} rather than as a flag on the game.
 * A game may be paused and resumed any number of times, and the distance chain has to be
 * able to reproduce — during a backfill months later — exactly which stretches did not
 * count. See {@link nl.vlettenrally.data.domain.PauseWindows} and
 * {@link TeamDistanceCalculator}.
 * <p>
 * Pause and resume are the only legal ways into and out of PAUSED; {@code PUT /games/{id}}
 * rejects the status directly so a game can never sit in PAUSED without the period row
 * that makes the freeze reproducible.
 */
@ApplicationScoped
public class GamePauseService {

    @Inject
    GameService gameService;

    @Inject
    GamePausePeriodRepository pausePeriodRepository;

    @Inject
    GamePauseWindowCache pauseWindowCache;

    @Inject
    WebSocketPublisher webSocketPublisher;

    @Inject
    AutomationEngine automationEngine;

    @Inject
    LocationIngestService locationIngestService;

    @Inject
    TransactionSynchronizationRegistry txRegistry;

    /**
     * Freeze a running game and open a pause period at {@code now}.
     * <p>
     * Fires {@code GAME_STATUS_CHANGE} automations for rules configured with
     * {@code toStatus = PAUSED}, which is how an admin wires "on emergency stop, message
     * every team".
     *
     * @throws GamePauseException 404 when the game does not exist, 409 when it is not ACTIVE
     */
    @Transactional
    public Game pause(UUID gameId, String reason, UUID pausedBy) {
        Game game = requireGame(gameId);
        if (game.getStatus() != Game.GameStatus.ACTIVE) {
            throw new GamePauseException(Response.Status.CONFLICT,
                "Only a running game can be paused (game is " + game.getStatus() + ").");
        }
        // Belt-and-braces against an inconsistent state (ACTIVE with a period nothing closed).
        // A genuine concurrent double-pause is caught one level down by the uq_gpp_open partial
        // unique index, which aborts the losing transaction rather than opening a second window.
        if (pausePeriodRepository.findOpenByGameId(gameId).isPresent()) {
            throw new GamePauseException(Response.Status.CONFLICT,
                "Game already has an open pause period.");
        }

        GamePausePeriod period = new GamePausePeriod();
        period.setGameId(gameId);
        period.setPausedAt(Instant.now());
        period.setReason(reason != null && !reason.isBlank() ? reason.trim() : null);
        period.setPausedBy(pausedBy);
        pausePeriodRepository.save(period);

        game.setStatus(Game.GameStatus.PAUSED);
        Game updated = gameService.save(game);

        afterCommit(() -> pauseWindowCache.invalidate(gameId));
        broadcastStatusAfterCommit(gameId, "paused", "active", period.getReason());

        Log.infof("Game '%s' paused%s", updated.getName(),
            period.getReason() != null ? " (" + period.getReason() + ")" : "");

        automationEngine.onGameStatusChange(updated, Game.GameStatus.ACTIVE, Game.GameStatus.PAUSED);
        return updated;
    }

    /**
     * Un-freeze a paused game and close its open period at {@code now}.
     * <p>
     * Deliberately does <em>not</em> fire {@code GAME_STATUS_CHANGE}. The engine dedupes
     * game-level rules on a {@code from->to} context key, so {@code PAUSED->ACTIVE} is a
     * fresh key: even a fire-once "the rally has started" rule would re-broadcast to every
     * team on every resume. Rules that genuinely want to react to a stop use
     * {@code toStatus = PAUSED}.
     * <p>
     * Ends by sweeping current positions, the mirror of what a kickoff does. Checkpoint
     * detection was frozen for the whole pause, so this is the only thing that credits a
     * team still sitting inside a zone when play restarts; a team that entered and left
     * again during the stop gets nothing, which is the intended cost of freezing.
     *
     * @throws GamePauseException 404 when the game does not exist, 409 when it is not PAUSED
     */
    @Transactional
    public Game resume(UUID gameId) {
        Game game = requireGame(gameId);
        if (game.getStatus() != Game.GameStatus.PAUSED) {
            throw new GamePauseException(Response.Status.CONFLICT,
                "Only a paused game can be resumed (game is " + game.getStatus() + ").");
        }

        // Also registers the window-cache invalidation, so resume does not repeat it.
        closeOpenPeriod(gameId, Instant.now());

        game.setStatus(Game.GameStatus.ACTIVE);
        Game updated = gameService.save(game);

        broadcastStatusAfterCommit(gameId, "active", "paused", null);

        Log.infof("Game '%s' resumed", updated.getName());

        // Before the scheduler's next tick can complete a game resumed past its end time:
        // this sweep is the only credit path for a team parked in a zone during the stop.
        locationIngestService.evaluateCurrentPositions(gameId);
        return updated;
    }

    /**
     * Close the game's open pause period at {@code at}, if it has one. Used by
     * {@link GameStatusScheduler} when a paused game reaches its end time — the game
     * genuinely ends then (the end time is never extended), and leaving a window open
     * past it would keep excluding fixes from a rebuild forever.
     *
     * @return the closed period, or empty when the game was not paused
     */
    @Transactional
    public Optional<GamePausePeriod> closeOpenPeriod(UUID gameId, Instant at) {
        Optional<GamePausePeriod> open = pausePeriodRepository.findOpenByGameId(gameId);
        open.ifPresent(period -> {
            // Guard the chk_gpp_times constraint: an end time earlier than the pause itself
            // (an admin editing endTime backwards mid-stop) would otherwise abort the close
            // and strand the window open.
            period.setResumedAt(at.isAfter(period.getPausedAt()) ? at : period.getPausedAt().plusMillis(1));
            pausePeriodRepository.save(period);
            afterCommit(() -> pauseWindowCache.invalidate(gameId));
        });
        return open;
    }

    /** The reason given for the game's current stop, if it is paused right now. */
    public Optional<String> currentPauseReason(UUID gameId) {
        return pausePeriodRepository.findOpenByGameId(gameId).map(GamePausePeriod::getReason);
    }

    private Game requireGame(UUID gameId) {
        return gameService.findById(gameId)
            .orElseThrow(() -> new GamePauseException(Response.Status.NOT_FOUND, "Game not found: " + gameId));
    }

    /**
     * Run {@code action} only once the transaction commits. Invalidating the window cache
     * inside the transaction would let a concurrent ingest on this replica re-populate it
     * from the pre-commit state and keep scoring through the stop until the TTL expired.
     */
    private void afterCommit(Runnable action) {
        try {
            txRegistry.registerInterposedSynchronization(new Synchronization() {
                @Override public void beforeCompletion() { }
                @Override public void afterCompletion(int status) {
                    if (status == Status.STATUS_COMMITTED) {
                        action.run();
                    }
                }
            });
        } catch (IllegalStateException noTx) {
            action.run();
        }
    }

    private void broadcastStatusAfterCommit(UUID gameId, String status, String previousStatus, String reason) {
        Map<String, Object> payload = new HashMap<>();
        payload.put("gameId", gameId);
        payload.put("status", status);
        payload.put("previousStatus", previousStatus);
        if (reason != null) {
            payload.put("reason", reason);
        }
        afterCommit(() ->
            webSocketPublisher.broadcastToGame(gameId, WebSocketEventTypes.GAME_STATUS, payload));
    }
}
