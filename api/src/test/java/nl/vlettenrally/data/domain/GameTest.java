package nl.vlettenrally.data.domain;

import static org.junit.jupiter.api.Assertions.assertEquals;
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

    private static final Instant START = Instant.parse("2026-06-30T12:00:00Z");
    private static final Instant END = Instant.parse("2026-06-30T18:00:00Z");

    private Game game() {
        Game game = new Game();
        game.setStartTime(START);
        game.setEndTime(END);
        return game;
    }

    @Test
    void newGame_defaultsPollingWindowsToTwoHours() {
        Game game = new Game();
        assertEquals(120, game.getLocationPollingLeadMinutes());
        assertEquals(120, game.getLocationPollingTailMinutes());
    }

    // --- Lead window: [start - lead, start) ---

    @Test
    void leadWindow_openJustBeforeStart() {
        Game game = game();
        game.setLocationPollingLeadMinutes(30);
        assertTrue(game.isWithinPollingLeadWindow(START.minus(Duration.ofMinutes(1))));
    }

    @Test
    void leadWindow_closedAtStart() {
        Game game = game();
        game.setLocationPollingLeadMinutes(30);
        assertFalse(game.isWithinPollingLeadWindow(START));
    }

    @Test
    void leadWindow_lowerBoundInclusive() {
        Game game = game();
        game.setLocationPollingLeadMinutes(30);
        assertTrue(game.isWithinPollingLeadWindow(START.minus(Duration.ofMinutes(30))));
        assertFalse(game.isWithinPollingLeadWindow(START.minus(Duration.ofMinutes(30)).minusSeconds(1)));
    }

    @Test
    void leadWindow_zeroOrNullDisables() {
        Game game = game();
        game.setLocationPollingLeadMinutes(0);
        assertFalse(game.isWithinPollingLeadWindow(START.minus(Duration.ofMinutes(5))));
        game.setLocationPollingLeadMinutes(null);
        assertFalse(game.isWithinPollingLeadWindow(START.minus(Duration.ofMinutes(5))));
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

    // --- checkInWindowClosesAt: whichever is later of checkedInAt+window and startTime+window ---

    @Test
    void checkInWindowClosesAt_noStartTime_fallsBackToCheckedInDeadline() {
        Game game = new Game();
        Instant checkedInAt = Instant.parse("2026-06-30T10:00:00Z");
        assertEquals(checkedInAt.plus(Duration.ofMinutes(30)), game.checkInWindowClosesAt(checkedInAt, 30));
    }

    @Test
    void checkInWindowClosesAt_earlyCheckIn_startDeadlineWins() {
        // Checked in well before the game starts: checkedInAt+30 is long past by the time
        // the game starts, so the window should extend to startTime+30 instead.
        Game game = game();
        Instant checkedInAt = START.minus(Duration.ofHours(2));
        assertEquals(START.plus(Duration.ofMinutes(30)), game.checkInWindowClosesAt(checkedInAt, 30));
    }

    @Test
    void checkInWindowClosesAt_lateCheckIn_checkedInDeadlineWins() {
        // Checked in after the game already started: startTime+30 has already passed, so
        // the team still gets its own full window from checkedInAt.
        Game game = game();
        Instant checkedInAt = START.plus(Duration.ofHours(1));
        assertEquals(checkedInAt.plus(Duration.ofMinutes(30)), game.checkInWindowClosesAt(checkedInAt, 30));
    }
}
