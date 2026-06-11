package nl.vlettenrally.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import jakarta.transaction.Status;
import jakarta.transaction.Synchronization;
import jakarta.transaction.TransactionSynchronizationRegistry;
import jakarta.ws.rs.BadRequestException;
import jakarta.ws.rs.NotFoundException;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import nl.vlettenrally.data.converter.PointEntryConverter;
import nl.vlettenrally.data.domain.Game;
import nl.vlettenrally.data.domain.PointEntry;
import nl.vlettenrally.data.domain.Team;
import nl.vlettenrally.data.dto.game.RankingResponse;
import nl.vlettenrally.data.enums.PointEntryType;
import nl.vlettenrally.data.repository.PointEntryRepository;
import nl.vlettenrally.data.repository.TeamRepository;
import nl.vlettenrally.websockets.WebSocketPublisher;
import nl.vlettenrally.websockets.dto.WebSocketMessageType;

/**
 * Unit tests for PointsLedgerService.
 * Verifies the ledger is the single mutator of Team.totalPoints and enforces
 * the non-negative-total invariant on both create and edit.
 */
@ExtendWith(MockitoExtension.class)
class PointsLedgerServiceTest {

    @Mock PointEntryRepository pointEntryRepository;
    @Mock TeamRepository teamRepository;
    @Mock WebSocketPublisher webSocketPublisher;
    @Mock PointEntryConverter pointEntryConverter;
    @Mock GameService gameService;
    @Mock RankingService rankingService;
    @Mock TransactionSynchronizationRegistry txRegistry;
    @Mock AutomationEngine automationEngine;

    @InjectMocks PointsLedgerService ledger;

    private UUID teamId;
    private UUID gameId;
    private Team team;

    @BeforeEach
    void setUp() {
        teamId = UUID.randomUUID();
        gameId = UUID.randomUUID();

        team = new Team();
        team.setId(teamId);
        team.setGameId(gameId);
        team.setName("Test team");
        team.setTotalPoints(100);

        lenient().when(pointEntryRepository.save(any(PointEntry.class)))
            .thenAnswer(inv -> inv.getArgument(0));
        lenient().when(teamRepository.save(any(Team.class)))
            .thenAnswer(inv -> inv.getArgument(0));
    }

    @Test
    void createEntry_addsAmountToTeamTotal_andRecordsEntry() {
        when(teamRepository.findById(teamId)).thenReturn(Optional.of(team));

        PointEntry result = ledger.createEntry(teamId, gameId, PointEntryType.CHECKPOINT_VERIFICATION,
            50, null, "Checkpoint X", UUID.randomUUID(), null);

        assertEquals(150, team.getTotalPoints());
        assertEquals(50, result.getOriginalAmount());
        assertEquals(50, result.getCurrentAmount());
        assertEquals(teamId, result.getTeamId());
        assertEquals(PointEntryType.CHECKPOINT_VERIFICATION, result.getEntryType());
        verify(teamRepository).save(team);
        verify(automationEngine).onTeamPointsChanged(gameId, teamId, 100, 150);
    }

    @Test
    void accrueEntry_appliesDelta_withoutTouchingEditAuditFields() {
        PointEntry entry = new PointEntry();
        entry.setTeamId(teamId);
        entry.setGameId(gameId);
        entry.setEntryType(PointEntryType.DISTANCE_BONUS);
        entry.setOriginalAmount(10);
        entry.setCurrentAmount(10);
        when(pointEntryRepository.findById(entry.getId())).thenReturn(Optional.of(entry));
        when(teamRepository.findById(teamId)).thenReturn(Optional.of(team));

        PointEntry result = ledger.accrueEntry(entry.getId(), 25);

        assertEquals(115, team.getTotalPoints());
        assertEquals(25, result.getCurrentAmount());
        assertEquals(10, result.getOriginalAmount());
        assertNull(result.getEditedAt());
        assertNull(result.getEditedBy());
        assertNull(result.getEditReason());
        verify(automationEngine).onTeamPointsChanged(gameId, teamId, 100, 115);
    }

    @Test
    void accrueEntry_noOp_whenAmountUnchanged() {
        PointEntry entry = new PointEntry();
        entry.setTeamId(teamId);
        entry.setGameId(gameId);
        entry.setCurrentAmount(10);
        when(pointEntryRepository.findById(entry.getId())).thenReturn(Optional.of(entry));

        ledger.accrueEntry(entry.getId(), 10);

        verify(teamRepository, never()).save(any());
        verify(pointEntryRepository, never()).save(any());
    }

    @Test
    void createEntry_appliesNegativeAmount_andDeductsFromTeamTotal() {
        when(teamRepository.findById(teamId)).thenReturn(Optional.of(team));

        ledger.createEntry(teamId, gameId, PointEntryType.MANUAL_ADJUSTMENT,
            -30, null, "Penalty", null, UUID.randomUUID());

        assertEquals(70, team.getTotalPoints());
    }

    @Test
    void createEntry_rejectsAmountThatDrivesTotalNegative() {
        when(teamRepository.findById(teamId)).thenReturn(Optional.of(team));

        assertThrows(BadRequestException.class, () ->
            ledger.createEntry(teamId, gameId, PointEntryType.MANUAL_ADJUSTMENT,
                -101, null, "Too much", null, null)
        );

        // Total unchanged, nothing persisted
        assertEquals(100, team.getTotalPoints());
        verify(pointEntryRepository, never()).save(any());
        verify(teamRepository, never()).save(any());
    }

    @Test
    void createEntry_throwsNotFound_whenTeamMissing() {
        when(teamRepository.findById(teamId)).thenReturn(Optional.empty());

        assertThrows(NotFoundException.class, () ->
            ledger.createEntry(teamId, gameId, PointEntryType.MANUAL_ADJUSTMENT,
                10, null, "Anything", null, null)
        );
    }

    @Test
    void editEntry_appliesDeltaToTeamTotal_andLeavesOriginalAmountUntouched() {
        UUID entryId = UUID.randomUUID();
        PointEntry existing = new PointEntry();
        existing.setId(entryId);
        existing.setTeamId(teamId);
        existing.setGameId(gameId);
        existing.setEntryType(PointEntryType.CHECKPOINT_VERIFICATION);
        existing.setOriginalAmount(120);
        existing.setCurrentAmount(120);

        when(pointEntryRepository.findById(entryId)).thenReturn(Optional.of(existing));
        when(teamRepository.findById(teamId)).thenReturn(Optional.of(team));

        UUID admin = UUID.randomUUID();
        PointEntry result = ledger.editEntry(entryId, 100, "Onreglementair gevaren", admin);

        assertEquals(100, result.getCurrentAmount());
        assertEquals(120, result.getOriginalAmount(), "originalAmount must stay immutable");
        assertEquals(admin, result.getEditedBy());
        assertEquals("Onreglementair gevaren", result.getEditReason());
        assertNotNull(result.getEditedAt());
        // Team total adjusted by delta -20
        assertEquals(80, team.getTotalPoints());
    }

    @Test
    void editEntry_rejectsEditThatDrivesTotalNegative() {
        UUID entryId = UUID.randomUUID();
        PointEntry existing = new PointEntry();
        existing.setId(entryId);
        existing.setTeamId(teamId);
        existing.setGameId(gameId);
        existing.setEntryType(PointEntryType.MANUAL_ADJUSTMENT);
        existing.setOriginalAmount(50);
        existing.setCurrentAmount(50);

        when(pointEntryRepository.findById(entryId)).thenReturn(Optional.of(existing));
        when(teamRepository.findById(teamId)).thenReturn(Optional.of(team));

        // Reducing by 200 would put team at -50
        assertThrows(BadRequestException.class, () ->
            ledger.editEntry(entryId, -150, "Too far", null)
        );

        assertEquals(100, team.getTotalPoints());
        assertEquals(50, existing.getCurrentAmount(), "Entry must not be mutated on rejection");
    }

    @Test
    void editEntry_noOpDelta_stillStampsAuditFields() {
        UUID entryId = UUID.randomUUID();
        PointEntry existing = new PointEntry();
        existing.setId(entryId);
        existing.setTeamId(teamId);
        existing.setGameId(gameId);
        existing.setEntryType(PointEntryType.MANUAL_ADJUSTMENT);
        existing.setOriginalAmount(50);
        existing.setCurrentAmount(50);

        when(pointEntryRepository.findById(entryId)).thenReturn(Optional.of(existing));

        UUID admin = UUID.randomUUID();
        ledger.editEntry(entryId, 50, "No change but record reason", admin);

        // Team should NOT have been looked up at all
        verify(teamRepository, never()).findById(any());
        verify(teamRepository, never()).save(any());
        assertNotNull(existing.getEditedAt());
        assertEquals(admin, existing.getEditedBy());
        assertEquals("No change but record reason", existing.getEditReason());
    }

    @Test
    void editEntry_throwsNotFound_whenEntryMissing() {
        UUID entryId = UUID.randomUUID();
        when(pointEntryRepository.findById(entryId)).thenReturn(Optional.empty());

        assertThrows(NotFoundException.class, () ->
            ledger.editEntry(entryId, 10, "any", null)
        );
    }

    @Test
    void createEntry_persistsSourceAchievementIdAndDescription() {
        when(teamRepository.findById(teamId)).thenReturn(Optional.of(team));

        UUID achievementId = UUID.randomUUID();
        ledger.createEntry(teamId, gameId, PointEntryType.CHECKPOINT_VERIFICATION,
            25, null, "Checkpoint Amstel", achievementId, null);

        ArgumentCaptor<PointEntry> captor = ArgumentCaptor.forClass(PointEntry.class);
        verify(pointEntryRepository).save(captor.capture());

        PointEntry saved = captor.getValue();
        assertEquals(achievementId, saved.getSourceAchievementId());
        assertEquals("Checkpoint Amstel", saved.getDescription());
        assertNull(saved.getCreatedBy(), "System-created entries have no createdBy");
    }

    @Test
    void createEntry_recordsCreatedByForManualAdjustment() {
        when(teamRepository.findById(teamId)).thenReturn(Optional.of(team));

        UUID admin = UUID.randomUUID();
        ledger.createEntry(teamId, gameId, PointEntryType.MANUAL_ADJUSTMENT,
            10, null, "Bonus", null, admin);

        ArgumentCaptor<PointEntry> captor = ArgumentCaptor.forClass(PointEntry.class);
        verify(pointEntryRepository, times(1)).save(captor.capture());

        assertEquals(admin, captor.getValue().getCreatedBy());
        assertEquals(PointEntryType.MANUAL_ADJUSTMENT, captor.getValue().getEntryType());
    }

    @Test
    void editEntry_doesNotMutateTeam_whenDeltaIsZero() {
        UUID entryId = UUID.randomUUID();
        PointEntry existing = new PointEntry();
        existing.setId(entryId);
        existing.setTeamId(teamId);
        existing.setGameId(gameId);
        existing.setCurrentAmount(40);
        existing.setOriginalAmount(40);

        when(pointEntryRepository.findById(eq(entryId))).thenReturn(Optional.of(existing));

        ledger.editEntry(entryId, 40, "reaffirm", null);

        assertEquals(100, team.getTotalPoints());
        verify(teamRepository, never()).save(any());
    }

    // ---- standings broadcast (gated on game status) ----------------------

    @Test
    @SuppressWarnings("unchecked")
    void createEntry_duringGame_sendsPerTeamRivalUpdates_andNoFullRankingBroadcast() {
        team.setCheckedIn(true); // X — the team whose points change
        when(teamRepository.findById(teamId)).thenReturn(Optional.of(team));

        // A picked X as a rival -> must be notified; B did not -> not notified;
        // C picked X but is not checked in -> not notified.
        Team a = teamInGame("A", List.of(teamId), true);
        Team b = teamInGame("B", List.of(UUID.randomUUID()), true);
        Team c = teamInGame("C", List.of(teamId), false);

        when(gameService.findById(gameId)).thenReturn(Optional.of(game(Game.GameStatus.ACTIVE)));
        when(teamRepository.findByGameId(gameId)).thenReturn(List.of(team, a, b, c));

        ledger.createEntry(teamId, gameId, PointEntryType.CHECKPOINT_VERIFICATION,
            50, null, "Checkpoint X", null, null);
        fireAfterCommit();

        // The full ranking is never broadcast while the game is running.
        verify(webSocketPublisher, never())
            .broadcastToGame(eq(gameId), eq(WebSocketMessageType.RANKING_UPDATE.getValue()), any());

        // X (own total moved) and A (picked X) each get a RIVAL_UPDATE; B and C do not.
        verify(webSocketPublisher).sendToTeam(eq(gameId), eq(teamId),
            eq(WebSocketMessageType.RIVAL_UPDATE.getValue()), any());
        verify(webSocketPublisher, never()).sendToTeam(eq(gameId), eq(b.getId()),
            eq(WebSocketMessageType.RIVAL_UPDATE.getValue()), any());
        verify(webSocketPublisher, never()).sendToTeam(eq(gameId), eq(c.getId()),
            eq(WebSocketMessageType.RIVAL_UPDATE.getValue()), any());

        // A's payload carries only X, with X's fresh total (100 + 50) — no other scores.
        ArgumentCaptor<Object> payload = ArgumentCaptor.forClass(Object.class);
        verify(webSocketPublisher).sendToTeam(eq(gameId), eq(a.getId()),
            eq(WebSocketMessageType.RIVAL_UPDATE.getValue()), payload.capture());
        List<Map<String, Object>> rivals = (List<Map<String, Object>>) payload.getValue();
        assertEquals(1, rivals.size());
        assertEquals(teamId, rivals.getFirst().get("id"));
        assertEquals(150, rivals.getFirst().get("totalPoints"));
    }

    @Test
    void createEntry_afterCompletion_broadcastsFullRanking_andNoPerTeamSends() {
        team.setCheckedIn(true);
        when(teamRepository.findById(teamId)).thenReturn(Optional.of(team));
        when(gameService.findById(gameId)).thenReturn(Optional.of(game(Game.GameStatus.COMPLETED)));
        List<RankingResponse> full = List.of(new RankingResponse(1, teamId, "Test team", 150));
        when(rankingService.buildRanking(gameId)).thenReturn(full);

        ledger.createEntry(teamId, gameId, PointEntryType.CHECKPOINT_VERIFICATION,
            50, null, "Checkpoint X", null, null);
        fireAfterCommit();

        verify(webSocketPublisher).broadcastToGame(eq(gameId),
            eq(WebSocketMessageType.RANKING_UPDATE.getValue()), eq(full));
        verify(webSocketPublisher, never()).sendToTeam(any(), any(),
            eq(WebSocketMessageType.RIVAL_UPDATE.getValue()), any());
    }

    /** Run every post-commit synchronization the call registered, as a real commit would. */
    private void fireAfterCommit() {
        ArgumentCaptor<Synchronization> captor = ArgumentCaptor.forClass(Synchronization.class);
        verify(txRegistry, atLeastOnce()).registerInterposedSynchronization(captor.capture());
        for (Synchronization sync : captor.getAllValues()) {
            sync.afterCompletion(Status.STATUS_COMMITTED);
        }
    }

    private Team teamInGame(String name, List<UUID> rivals, boolean checkedIn) {
        Team t = new Team();
        t.setGameId(gameId);
        t.setName(name);
        t.setCheckedIn(checkedIn);
        t.setRivals(new ArrayList<>(rivals));
        return t;
    }

    private Game game(Game.GameStatus status) {
        Game g = new Game();
        g.setId(gameId);
        g.setStatus(status);
        return g;
    }
}
