package nl.vlettenrally.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
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
import nl.vlettenrally.data.repository.TeamRepository;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * Unit tests for the late-finish penalty: when it fires, when it stays silent, and that it
 * never fails the finish it is attached to.
 */
@ExtendWith(MockitoExtension.class)
class LateFinishPenaltyServiceTest {

    @Mock GameService gameService;
    @Mock PointEntryRepository pointEntryRepository;
    @Mock PointsLedgerService pointsLedgerService;
    @Mock PointsTotalsCache pointsTotalsCache;
    @Mock TeamRepository teamRepository;

    @InjectMocks LateFinishPenaltyService service;

    private static final Instant END = Instant.parse("2026-08-05T18:00:00Z");

    private final UUID gameId = UUID.randomUUID();
    private Game game;

    @BeforeEach
    void setUp() {
        game = new Game();
        game.setId(gameId);
        game.setEndTime(END);
        game.setLateFinishPenaltyPoints(50);
        lenient().when(gameService.findById(gameId)).thenReturn(Optional.of(game));
        lenient().when(pointEntryRepository.findByGame(eq(gameId), any(), eq(PointEntryType.LATE_FINISH_PENALTY),
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
    void lateFinish_deductsThePenaltyOnce() {
        Team team = finishedAt(END.plusSeconds(20 * 60));
        when(pointsTotalsCache.get(team.getId())).thenReturn(300);

        assertTrue(service.applyIfLate(team).isPresent());

        ArgumentCaptor<Integer> amount = ArgumentCaptor.forClass(Integer.class);
        ArgumentCaptor<String> description = ArgumentCaptor.forClass(String.class);
        verify(pointsLedgerService).createEntry(eq(team.getId()), eq(gameId),
            eq(PointEntryType.LATE_FINISH_PENALTY), amount.capture(), any(),
            description.capture(), any(), any());
        assertEquals(-50, amount.getValue());
        assertEquals("Te laat gefinisht (20 min na einde)", description.getValue());
    }

    @Test
    void onTimeFinish_isNotPenalised() {
        Team team = finishedAt(END.minusSeconds(60));

        assertTrue(service.applyIfLate(team).isEmpty());
        verifyNoPenalty();
    }

    @Test
    void finishExactlyAtTheEndTime_isOnTime() {
        // The boundary is exclusive: finishing on the stroke of the end time counts as in.
        Team team = finishedAt(END);

        assertTrue(service.applyIfLate(team).isEmpty());
        verifyNoPenalty();
    }

    @Test
    void unfinishedTeam_isNotPenalised() {
        assertTrue(service.applyIfLate(finishedAt(null)).isEmpty());
        verifyNoPenalty();
    }

    @Test
    void secondCall_doesNotChargeTwice() {
        Team team = finishedAt(END.plusSeconds(600));
        when(pointEntryRepository.findByGame(eq(gameId), eq(team.getId()),
            eq(PointEntryType.LATE_FINISH_PENALTY), anyInt(), anyInt()))
            .thenReturn(List.of(new PointEntry()));

        assertTrue(service.applyIfLate(team).isEmpty(),
            "an un-finish/re-finish cycle must not charge the penalty again");
        verifyNoPenalty();
    }

    @Test
    void penaltyIsClampedToTheTeamsAvailablePoints() {
        // PointsLedgerService rejects a total below zero; a poor team must still be able to
        // finish, so the deduction is capped at what it actually has.
        Team team = finishedAt(END.plusSeconds(600));
        when(pointsTotalsCache.get(team.getId())).thenReturn(20);

        assertTrue(service.applyIfLate(team).isPresent());

        ArgumentCaptor<Integer> amount = ArgumentCaptor.forClass(Integer.class);
        verify(pointsLedgerService).createEntry(any(), any(), any(), amount.capture(), any(), any(), any(), any());
        assertEquals(-20, amount.getValue());
    }

    @Test
    void teamWithNoPoints_getsNoZeroAmountEntry() {
        Team team = finishedAt(END.plusSeconds(600));
        when(pointsTotalsCache.get(team.getId())).thenReturn(0);

        assertTrue(service.applyIfLate(team).isEmpty());
        verifyNoPenalty();
    }

    @Test
    void penaltyOfZero_disablesTheFeatureForThatGame() {
        game.setLateFinishPenaltyPoints(0);

        assertTrue(service.applyIfLate(finishedAt(END.plusSeconds(600))).isEmpty());
        verifyNoPenalty();
    }

    @Test
    void gameWithoutAnEndTime_hasNothingToBeLateFor() {
        game.setEndTime(null);

        assertTrue(service.applyIfLate(finishedAt(END.plusSeconds(600))).isEmpty());
        verifyNoPenalty();
    }

    @Test
    void missingGame_isIgnoredRatherThanThrown() {
        when(gameService.findById(gameId)).thenReturn(Optional.empty());

        assertTrue(service.applyIfLate(finishedAt(END.plusSeconds(600))).isEmpty());
        verifyNoPenalty();
    }

    // ---- DNF at the game's end ---------------------------------------------

    @Test
    void gameCompleting_chargesEveryCheckedInTeamStillOut() {
        Team stillOut = finishedAt(null);
        Team alsoOut = finishedAt(null);
        Team alreadyIn = finishedAt(END.minusSeconds(300));
        lenient().when(pointsTotalsCache.get(stillOut.getId())).thenReturn(300);
        lenient().when(pointsTotalsCache.get(alsoOut.getId())).thenReturn(300);
        when(teamRepository.findByGameId(gameId)).thenReturn(List.of(stillOut, alreadyIn, alsoOut));

        assertEquals(2, service.applyToUnfinishedTeams(game));

        verify(pointsLedgerService).createEntry(eq(stillOut.getId()), eq(gameId),
            eq(PointEntryType.LATE_FINISH_PENALTY), eq(-50), any(),
            eq("Niet gefinisht bij einde spel"), any(), any());
        verify(pointsLedgerService, never()).createEntry(eq(alreadyIn.getId()), any(), any(),
            anyInt(), any(), any(), any(), any());
    }

    @Test
    void gameCompleting_skipsTeamsThatNeverCheckedIn() {
        // A no-show did not start, which is a different thing from a DNF.
        Team noShow = finishedAt(null);
        noShow.setCheckedIn(false);
        when(teamRepository.findByGameId(gameId)).thenReturn(List.of(noShow));

        assertEquals(0, service.applyToUnfinishedTeams(game));
        verifyNoPenalty();
    }

    @Test
    void dnfTeamThatLaterHandsInItsTracker_isNotChargedTwice() {
        // The tracker-handover case end to end: charged at the game's end, then marked finished.
        Team team = finishedAt(null);
        when(pointsTotalsCache.get(team.getId())).thenReturn(300);
        when(teamRepository.findByGameId(gameId)).thenReturn(List.of(team));

        assertEquals(1, service.applyToUnfinishedTeams(game));

        // The desk marks it finished 45 minutes after the end; the DNF entry now exists.
        team.setFinished(true);
        team.setFinishedAt(END.plusSeconds(45 * 60));
        when(pointEntryRepository.findByGame(eq(gameId), eq(team.getId()),
            eq(PointEntryType.LATE_FINISH_PENALTY), anyInt(), anyInt()))
            .thenReturn(List.of(new PointEntry()));

        assertTrue(service.applyIfLate(team).isEmpty(), "the finish must not charge a second time");
        verify(pointsLedgerService, times(1)).createEntry(any(), any(), any(), anyInt(), any(),
            any(), any(), any());
    }

    @Test
    void gameCompleting_withNoPenaltyConfigured_readsNoTeams() {
        game.setLateFinishPenaltyPoints(0);

        assertEquals(0, service.applyToUnfinishedTeams(game));
        verifyNoPenalty();
        verify(teamRepository, never()).findByGameId(any());
    }

    @Test
    void gameCompleting_oneTeamFailing_stillChargesTheRest() {
        Team failing = finishedAt(null);
        Team ok = finishedAt(null);
        when(pointsTotalsCache.get(failing.getId())).thenReturn(300);
        when(pointsTotalsCache.get(ok.getId())).thenReturn(300);
        when(teamRepository.findByGameId(gameId)).thenReturn(List.of(failing, ok));
        when(pointsLedgerService.createEntry(eq(failing.getId()), any(), any(), anyInt(), any(),
            any(), any(), any())).thenThrow(new RuntimeException("ledger down"));

        assertEquals(1, service.applyToUnfinishedTeams(game),
            "one team's ledger failing must not strand the rest");
        verify(pointsLedgerService).createEntry(eq(ok.getId()), any(), any(), anyInt(), any(),
            any(), any(), any());
    }

    private void verifyNoPenalty() {
        verify(pointsLedgerService, never())
            .createEntry(any(), any(), any(), anyInt(), any(), any(), any(), any());
    }
}
