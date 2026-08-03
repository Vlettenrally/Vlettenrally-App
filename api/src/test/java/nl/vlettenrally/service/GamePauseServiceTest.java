package nl.vlettenrally.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import jakarta.transaction.Synchronization;
import jakarta.transaction.TransactionSynchronizationRegistry;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import nl.vlettenrally.data.domain.Game;
import nl.vlettenrally.data.domain.GamePausePeriod;
import nl.vlettenrally.data.repository.mock.MockGamePausePeriodRepository;
import nl.vlettenrally.websockets.WebSocketEventTypes;
import nl.vlettenrally.websockets.WebSocketPublisher;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * Behavioural tests for the emergency stop. Uses the real in-memory pause-period repository so
 * the "at most one open period" rule and the open/close bookkeeping are exercised for real;
 * everything the service reaches out to (game store, sockets, automations) is mocked.
 * <p>
 * Calling the methods on a plain instance bypasses the {@code @Transactional} interceptor, so the
 * after-commit hooks fall through to their no-transaction branch and run inline — which is exactly
 * what lets these tests assert on them.
 */
@ExtendWith(MockitoExtension.class)
class GamePauseServiceTest {

    private static final UUID GAME_ID = UUID.fromString("a1b2c3d4-e5f6-7890-abcd-ef1234567890");
    private static final UUID ADMIN_ID = UUID.fromString("dddddddd-dddd-dddd-dddd-dddddddddddd");

    @Mock GameService gameService;
    @Mock GamePauseWindowCache pauseWindowCache;
    @Mock WebSocketPublisher webSocketPublisher;
    @Mock AutomationEngine automationEngine;
    @Mock LocationIngestService locationIngestService;
    @Mock TransactionSynchronizationRegistry txRegistry;

    private MockGamePausePeriodRepository pauseRepo;
    private GamePauseService service;
    private Game game;

    @BeforeEach
    void setUp() {
        pauseRepo = new MockGamePausePeriodRepository();

        service = new GamePauseService();
        service.gameService = gameService;
        service.pausePeriodRepository = pauseRepo;
        service.pauseWindowCache = pauseWindowCache;
        service.webSocketPublisher = webSocketPublisher;
        service.automationEngine = automationEngine;
        service.locationIngestService = locationIngestService;
        // Stand in for a transaction that commits: every registered synchronization runs its
        // committed path immediately, so the tests assert on what a real commit would produce
        // rather than on the no-transaction fallback.
        service.txRegistry = txRegistry;
        lenient().doAnswer(invocation -> {
            Synchronization sync = invocation.getArgument(0);
            sync.beforeCompletion();
            sync.afterCompletion(jakarta.transaction.Status.STATUS_COMMITTED);
            return null;
        }).when(txRegistry).registerInterposedSynchronization(any());

        game = new Game();
        game.setId(GAME_ID);
        game.setName("Test rally");
        game.setStatus(Game.GameStatus.ACTIVE);
        lenient().when(gameService.findById(GAME_ID)).thenReturn(Optional.of(game));
        lenient().when(gameService.save(any(Game.class))).thenAnswer(i -> i.getArgument(0));
    }

    @Test
    void pause_freezesTheGameAndOpensAPeriod() {
        Game paused = service.pause(GAME_ID, "  Boat capsized at the bridge  ", ADMIN_ID);

        assertEquals(Game.GameStatus.PAUSED, paused.getStatus());

        GamePausePeriod open = pauseRepo.findOpenByGameId(GAME_ID).orElseThrow();
        assertEquals("Boat capsized at the bridge", open.getReason(), "reason is trimmed");
        assertEquals(ADMIN_ID, open.getPausedBy());
        assertNotNull(open.getPausedAt());
        assertNull(open.getResumedAt());
    }

    @Test
    void pause_broadcastsTheStatusWithItsReason() {
        service.pause(GAME_ID, "Thunderstorm", ADMIN_ID);

        @SuppressWarnings("unchecked")
        ArgumentCaptor<java.util.Map<String, Object>> payload = ArgumentCaptor.forClass(java.util.Map.class);
        verify(webSocketPublisher).broadcastToGame(eq(GAME_ID), eq(WebSocketEventTypes.GAME_STATUS), payload.capture());
        assertEquals("paused", payload.getValue().get("status"));
        assertEquals("active", payload.getValue().get("previousStatus"));
        assertEquals("Thunderstorm", payload.getValue().get("reason"));
    }

    @Test
    void pause_firesGameStatusChangeSoRulesCanReactToTheStop() {
        service.pause(GAME_ID, null, ADMIN_ID);

        verify(automationEngine).onGameStatusChange(any(Game.class),
            eq(Game.GameStatus.ACTIVE), eq(Game.GameStatus.PAUSED));
    }

    @Test
    void pause_blankReason_isStoredAsNullRatherThanEmpty() {
        service.pause(GAME_ID, "   ", ADMIN_ID);

        assertNull(pauseRepo.findOpenByGameId(GAME_ID).orElseThrow().getReason());
    }

    @Test
    void pause_rejectsAGameThatIsNotRunning() {
        for (Game.GameStatus status : List.of(Game.GameStatus.PLANNED, Game.GameStatus.COMPLETED)) {
            game.setStatus(status);
            GamePauseException e = assertThrows(GamePauseException.class,
                () -> service.pause(GAME_ID, null, ADMIN_ID));
            assertEquals(409, e.getStatusCode());
        }
        assertTrue(pauseRepo.findByGameId(GAME_ID).isEmpty());
    }

    @Test
    void pause_rejectsASecondPauseWhileOneIsOpen() {
        service.pause(GAME_ID, null, ADMIN_ID);
        // Status alone catches the normal double-click...
        GamePauseException e = assertThrows(GamePauseException.class,
            () -> service.pause(GAME_ID, null, ADMIN_ID));
        assertEquals(409, e.getStatusCode());

        // ...and so does the open-period check, if the status ever drifted out of sync.
        game.setStatus(Game.GameStatus.ACTIVE);
        GamePauseException drifted = assertThrows(GamePauseException.class,
            () -> service.pause(GAME_ID, null, ADMIN_ID));
        assertEquals(409, drifted.getStatusCode());
        assertEquals(1, pauseRepo.findByGameId(GAME_ID).size());
    }

    @Test
    void resume_closesExactlyOnePeriodAndSweepsPositions() {
        service.pause(GAME_ID, null, ADMIN_ID);
        Game resumed = service.resume(GAME_ID);

        assertEquals(Game.GameStatus.ACTIVE, resumed.getStatus());
        assertTrue(pauseRepo.findOpenByGameId(GAME_ID).isEmpty());
        assertEquals(1, pauseRepo.findByGameId(GAME_ID).size());
        assertNotNull(pauseRepo.findByGameId(GAME_ID).getFirst().getResumedAt());

        // The only credit path for a team parked inside a zone for the whole stop.
        verify(locationIngestService).evaluateCurrentPositions(GAME_ID);
    }

    @Test
    void resume_doesNotFireGameStatusChange() {
        // The engine dedupes on a from->to key, so PAUSED->ACTIVE would be a fresh key and every
        // fire-once "the rally has started" rule would re-broadcast on each resume.
        service.pause(GAME_ID, null, ADMIN_ID);
        service.resume(GAME_ID);

        verify(automationEngine, never()).onGameStatusChange(any(), any(), eq(Game.GameStatus.ACTIVE));
    }

    @Test
    void resume_rejectsAGameThatIsNotPaused() {
        GamePauseException e = assertThrows(GamePauseException.class, () -> service.resume(GAME_ID));
        assertEquals(409, e.getStatusCode());
        verify(locationIngestService, never()).evaluateCurrentPositions(any());
    }

    @Test
    void pauseAndResume_repeatedly_accumulatesSeparatePeriods() {
        for (int i = 0; i < 3; i++) {
            service.pause(GAME_ID, "stop " + i, ADMIN_ID);
            service.resume(GAME_ID);
        }

        List<GamePausePeriod> periods = pauseRepo.findByGameId(GAME_ID);
        assertEquals(3, periods.size());
        assertTrue(periods.stream().noneMatch(GamePausePeriod::isOpen));
        for (GamePausePeriod p : periods) {
            assertTrue(p.getResumedAt().isAfter(p.getPausedAt()));
        }
    }

    @Test
    void closeOpenPeriod_clampsAnEndTimeThatPredatesTheStop() {
        // An admin editing endTime backwards mid-stop would otherwise write resumedAt <= pausedAt
        // and trip chk_gpp_times, leaving the window open forever.
        service.pause(GAME_ID, null, ADMIN_ID);
        Instant beforeThePause = pauseRepo.findOpenByGameId(GAME_ID).orElseThrow()
            .getPausedAt().minusSeconds(600);

        service.closeOpenPeriod(GAME_ID, beforeThePause);

        GamePausePeriod closed = pauseRepo.findByGameId(GAME_ID).getFirst();
        assertTrue(closed.getResumedAt().isAfter(closed.getPausedAt()));
    }

    @Test
    void closeOpenPeriod_onAGameThatWasNeverPaused_isANoOp() {
        assertTrue(service.closeOpenPeriod(GAME_ID, Instant.now()).isEmpty());
        assertTrue(pauseRepo.findByGameId(GAME_ID).isEmpty());
    }

    @Test
    void pauseAndResume_invalidateTheWindowCache() {
        service.pause(GAME_ID, null, ADMIN_ID);
        service.resume(GAME_ID);

        // Once per transition — a replica still serving an open window after a resume would
        // silently discard real distance for the rest of the cache TTL.
        verify(pauseWindowCache, org.mockito.Mockito.times(2)).invalidate(GAME_ID);
    }
}
