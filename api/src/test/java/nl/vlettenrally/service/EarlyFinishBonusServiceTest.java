package nl.vlettenrally.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import nl.vlettenrally.data.domain.Game;
import nl.vlettenrally.data.domain.PointEntry;
import nl.vlettenrally.data.domain.Team;
import nl.vlettenrally.data.enums.PointEntryType;
import nl.vlettenrally.data.repository.PointEntryRepository;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * Unit tests for the early-finish bonus: it fires only inside the qualifying window
 * [endTime - minutes, endTime], never for finishes earlier than that window, and never twice.
 */
@ExtendWith(MockitoExtension.class)
class EarlyFinishBonusServiceTest {

    @Mock GameService gameService;
    @Mock PointEntryRepository pointEntryRepository;
    @Mock PointsLedgerService pointsLedgerService;

    @InjectMocks EarlyFinishBonusService service;

    private static final Instant END = Instant.parse("2026-08-05T18:00:00Z");

    private final UUID gameId = UUID.randomUUID();
    private Game game;

    @BeforeEach
    void setUp() {
        game = new Game();
        game.setId(gameId);
        game.setEndTime(END);
        game.setEarlyFinishBonusPoints(50);
        game.setEarlyFinishBonusMinutes(60);
        lenient().when(gameService.findById(gameId)).thenReturn(Optional.of(game));
        lenient().when(pointEntryRepository.findByGame(eq(gameId), any(), eq(PointEntryType.EARLY_FINISH_BONUS),
            anyInt(), anyInt())).thenReturn(List.of());
        lenient().when(pointsLedgerService.createEntry(any(), any(), any(), anyInt(), any(), any(), any(), any()))
            .thenReturn(new PointEntry());
    }

    private Team finishedAt(Instant when) {
        Team t = new Team();
        t.setId(UUID.randomUUID());
        t.setGameId(gameId);
        t.setName("De Watervlooien");
        t.setCheckedIn(true);
        t.setFinished(when != null);
        t.setFinishedAt(when);
        return t;
    }

    @Test
    void finishInsideTheWindow_awardsTheBonusOnce() {
        Team team = finishedAt(END.minusSeconds(30 * 60));

        assertTrue(service.applyIfEarly(team).isPresent());

        ArgumentCaptor<Integer> amount = ArgumentCaptor.forClass(Integer.class);
        ArgumentCaptor<String> description = ArgumentCaptor.forClass(String.class);
        verify(pointsLedgerService).createEntry(eq(team.getId()), eq(gameId),
            eq(PointEntryType.EARLY_FINISH_BONUS), amount.capture(), any(),
            description.capture(), any(), any());
        assertEquals(50, amount.getValue());
        assertEquals("Vroeg gefinisht (30 min voor einde)", description.getValue());
    }

    @Test
    void finishTooEarly_outsideTheWindow_getsNothing() {
        // Well outside the 60-minute window: an open-ended "the earlier the better" rule would
        // wrongly award this. The rule is a window, not a threshold.
        Team team = finishedAt(END.minusSeconds(90 * 60));

        assertTrue(service.applyIfEarly(team).isEmpty());
        verifyNoBonus();
    }

    @Test
    void finishExactlyAtTheWindowsEarlyBoundary_qualifies() {
        Team team = finishedAt(END.minusSeconds(60 * 60));

        assertTrue(service.applyIfEarly(team).isPresent());
    }

    @Test
    void finishExactlyAtTheEndTime_qualifies() {
        Team team = finishedAt(END);

        assertTrue(service.applyIfEarly(team).isPresent());
    }

    @Test
    void finishOneInstantAfterTheEndTime_getsNothing() {
        // Falls to the separate late-finish penalty path instead.
        Team team = finishedAt(END.plusSeconds(1));

        assertTrue(service.applyIfEarly(team).isEmpty());
        verifyNoBonus();
    }

    @Test
    void unfinishedTeam_getsNothing() {
        assertTrue(service.applyIfEarly(finishedAt(null)).isEmpty());
        verifyNoBonus();
    }

    @Test
    void secondCall_doesNotAwardTwice() {
        Team team = finishedAt(END.minusSeconds(600));
        when(pointEntryRepository.findByGame(eq(gameId), eq(team.getId()),
            eq(PointEntryType.EARLY_FINISH_BONUS), anyInt(), anyInt()))
            .thenReturn(List.of(new PointEntry()));

        assertTrue(service.applyIfEarly(team).isEmpty(),
            "an un-finish/re-finish cycle must not award the bonus again");
        verifyNoBonus();
    }

    @Test
    void bonusOfZero_disablesTheFeatureForThatGame() {
        game.setEarlyFinishBonusPoints(0);

        assertTrue(service.applyIfEarly(finishedAt(END.minusSeconds(600))).isEmpty());
        verifyNoBonus();
    }

    @Test
    void missingWindowMinutes_disablesTheFeatureForThatGame() {
        game.setEarlyFinishBonusMinutes(null);

        assertTrue(service.applyIfEarly(finishedAt(END.minusSeconds(600))).isEmpty());
        verifyNoBonus();
    }

    @Test
    void gameWithoutAnEndTime_hasNoWindowToBeEarlyWithin() {
        game.setEndTime(null);

        assertTrue(service.applyIfEarly(finishedAt(END.minusSeconds(600))).isEmpty());
        verifyNoBonus();
    }

    @Test
    void missingGame_isIgnoredRatherThanThrown() {
        when(gameService.findById(gameId)).thenReturn(Optional.empty());

        assertTrue(service.applyIfEarly(finishedAt(END.minusSeconds(600))).isEmpty());
        verifyNoBonus();
    }

    private void verifyNoBonus() {
        verify(pointsLedgerService, never())
            .createEntry(any(), any(), any(), anyInt(), any(), any(), any(), any());
    }
}
