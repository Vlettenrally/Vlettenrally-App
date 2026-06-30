package nl.vlettenrally.data.domain;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Duration;
import java.time.Instant;

import org.junit.jupiter.api.Test;

/**
 * Unit tests for the polling-window helpers on {@link Game}. These are the single source
 * of truth shared by the poller (which games to fetch) and the ingest gate (whether to
 * detect checkpoint passes), so their boundaries are tested explicitly.
 */
class GameTest {

    private static final Instant SIGNAL = Instant.parse("2026-06-30T12:00:00Z");
    private static final Instant END = Instant.parse("2026-06-30T18:00:00Z");

    private Game game() {
        Game game = new Game();
        game.setStartSignalTime(SIGNAL);
        game.setEndTime(END);
        return game;
    }

    // --- Lead window: [signal - lead, signal) ---

    @Test
    void leadWindow_openJustBeforeSignal() {
        Game game = game();
        game.setLocationPollingLeadMinutes(30);
        assertTrue(game.isWithinPollingLeadWindow(SIGNAL.minus(Duration.ofMinutes(1))));
    }

    @Test
    void leadWindow_closedAtSignal() {
        Game game = game();
        game.setLocationPollingLeadMinutes(30);
        assertFalse(game.isWithinPollingLeadWindow(SIGNAL));
    }

    @Test
    void leadWindow_lowerBoundInclusive() {
        Game game = game();
        game.setLocationPollingLeadMinutes(30);
        assertTrue(game.isWithinPollingLeadWindow(SIGNAL.minus(Duration.ofMinutes(30))));
        assertFalse(game.isWithinPollingLeadWindow(SIGNAL.minus(Duration.ofMinutes(30)).minusSeconds(1)));
    }

    @Test
    void leadWindow_zeroOrNullDisables() {
        Game game = game();
        game.setLocationPollingLeadMinutes(0);
        assertFalse(game.isWithinPollingLeadWindow(SIGNAL.minus(Duration.ofMinutes(5))));
        game.setLocationPollingLeadMinutes(null);
        assertFalse(game.isWithinPollingLeadWindow(SIGNAL.minus(Duration.ofMinutes(5))));
    }

    // --- Tail window: (endTime, endTime + tail] ---

    @Test
    void tailWindow_closedAtEnd() {
        Game game = game();
        game.setLocationPollingTailMinutes(30);
        assertFalse(game.isWithinPollingTailWindow(END));
    }

    @Test
    void tailWindow_openJustAfterEnd() {
        Game game = game();
        game.setLocationPollingTailMinutes(30);
        assertTrue(game.isWithinPollingTailWindow(END.plusSeconds(1)));
    }

    @Test
    void tailWindow_upperBoundInclusive() {
        Game game = game();
        game.setLocationPollingTailMinutes(30);
        assertTrue(game.isWithinPollingTailWindow(END.plus(Duration.ofMinutes(30))));
        assertFalse(game.isWithinPollingTailWindow(END.plus(Duration.ofMinutes(30)).plusSeconds(1)));
    }

    @Test
    void tailWindow_zeroOrNullDisables() {
        Game game = game();
        game.setLocationPollingTailMinutes(0);
        assertFalse(game.isWithinPollingTailWindow(END.plus(Duration.ofMinutes(5))));
        game.setLocationPollingTailMinutes(null);
        assertFalse(game.isWithinPollingTailWindow(END.plus(Duration.ofMinutes(5))));
    }

    @Test
    void tailWindow_nullEndTimeDisables() {
        Game game = game();
        game.setEndTime(null);
        game.setLocationPollingTailMinutes(30);
        assertFalse(game.isWithinPollingTailWindow(Instant.now()));
    }
}
