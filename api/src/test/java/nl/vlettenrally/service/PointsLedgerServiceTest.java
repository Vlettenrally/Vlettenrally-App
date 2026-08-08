package nl.vlettenrally.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
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
import java.util.function.Supplier;

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
import nl.vlettenrally.websockets.AdminTopics;
import nl.vlettenrally.websockets.WebSocketMessage;
import nl.vlettenrally.websockets.WebSocketPublisher;
import nl.vlettenrally.websockets.dto.WebSocketMessageType;

/**
 * Unit tests for PointsLedgerService.
 * Verifies that the ledger is the authoritative source for team totals and that
 * those totals are maintained in PointsTotalsCache (not on the Team row).
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
    @Mock PointsTotalsCache pointsTotalsCache;
    @Mock nl.vlettenrally.websockets.StandingsBroadcastBuffer standingsBroadcastBuffer;

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
        // Initial total comes from the cache, not the team object
        lenient().when(pointsTotalsCache.get(teamId)).thenReturn(100);

        lenient().when(pointEntryRepository.save(any(PointEntry.class)))
            .thenAnswer(inv -> inv.getArgument(0));
    }

    @Test
    void createEntry_addsAmountToCache_andRecordsEntry() {
        when(teamRepository.findById(teamId)).thenReturn(Optional.of(team));

        PointEntry result = ledger.createEntry(teamId, gameId, PointEntryType.CHECKPOINT_VERIFICATION,
            50, null, "Checkpoint X", UUID.randomUUID(), null);

        assertEquals(50, result.getOriginalAmount());
        assertEquals(50, result.getCurrentAmount());
        assertEquals(teamId, result.getTeamId());
        assertEquals(PointEntryType.CHECKPOINT_VERIFICATION, result.getEntryType());
        // Team row is never touched for points
        verify(teamRepository, never()).save(any());
        verify(automationEngine).onTeamPointsChanged(gameId, teamId, 100, 150);
        // Cache is written after commit
        fireAfterCommit();
        verify(pointsTotalsCache).set(teamId, 150);
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

        PointEntry result = ledger.accrueEntry(entry.getId(), 25);

        assertEquals(25, result.getCurrentAmount());
        assertEquals(10, result.getOriginalAmount());
        assertNull(result.getEditedAt());
        assertNull(result.getEditedBy());
        assertNull(result.getEditReason());
        verify(teamRepository, never()).save(any());
        verify(automationEngine).onTeamPointsChanged(gameId, teamId, 100, 115);
        fireAfterCommit();
        verify(pointsTotalsCache).set(teamId, 115);
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
        verify(pointsTotalsCache, never()).set(any(), anyInt());
    }

    @Test
    void createEntry_appliesNegativeAmount() {
        when(teamRepository.findById(teamId)).thenReturn(Optional.of(team));

        ledger.createEntry(teamId, gameId, PointEntryType.MANUAL_ADJUSTMENT,
            -30, null, "Penalty", null, UUID.randomUUID());

        verify(automationEngine).onTeamPointsChanged(gameId, teamId, 100, 70);
        fireAfterCommit();
        verify(pointsTotalsCache).set(teamId, 70);
    }

    @Test
    void createEntry_rejectsAmountThatDrivesTotalNegative() {
        when(teamRepository.findById(teamId)).thenReturn(Optional.of(team));

        assertThrows(BadRequestException.class, () ->
            ledger.createEntry(teamId, gameId, PointEntryType.MANUAL_ADJUSTMENT,
                -101, null, "Too much", null, null)
        );

        verify(pointEntryRepository, never()).save(any());
        verify(teamRepository, never()).save(any());
        verify(pointsTotalsCache, never()).set(any(), anyInt());
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
    void editEntry_appliesDeltaToCache_andLeavesOriginalAmountUntouched() {
        UUID entryId = UUID.randomUUID();
        PointEntry existing = new PointEntry();
        existing.setId(entryId);
        existing.setTeamId(teamId);
        existing.setGameId(gameId);
        existing.setEntryType(PointEntryType.CHECKPOINT_VERIFICATION);
        existing.setOriginalAmount(120);
        existing.setCurrentAmount(120);

        when(pointEntryRepository.findById(entryId)).thenReturn(Optional.of(existing));

        UUID admin = UUID.randomUUID();
        PointEntry result = ledger.editEntry(entryId, 100, "Onreglementair gevaren", admin);

        assertEquals(100, result.getCurrentAmount());
        assertEquals(120, result.getOriginalAmount(), "originalAmount must stay immutable");
        assertEquals(admin, result.getEditedBy());
        assertEquals("Onreglementair gevaren", result.getEditReason());
        assertNotNull(result.getEditedAt());
        verify(teamRepository, never()).save(any());
        // delta = -20; cache updated to 100 + (-20) = 80
        fireAfterCommit();
        verify(pointsTotalsCache).set(teamId, 80);
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

        // Reducing by 200 would put team at -50
        assertThrows(BadRequestException.class, () ->
            ledger.editEntry(entryId, -150, "Too far", null)
        );

        assertEquals(50, existing.getCurrentAmount(), "Entry must not be mutated on rejection");
        verify(pointsTotalsCache, never()).set(any(), anyInt());
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

        // No total change → cache not written, team not saved
        verify(teamRepository, never()).findById(any());
        verify(teamRepository, never()).save(any());
        verify(pointsTotalsCache, never()).set(any(), anyInt());
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
    void editEntry_doesNotMutateCache_whenDeltaIsZero() {
        UUID entryId = UUID.randomUUID();
        PointEntry existing = new PointEntry();
        existing.setId(entryId);
        existing.setTeamId(teamId);
        existing.setGameId(gameId);
        existing.setCurrentAmount(40);
        existing.setOriginalAmount(40);

        when(pointEntryRepository.findById(eq(entryId))).thenReturn(Optional.of(existing));

        ledger.editEntry(entryId, 40, "reaffirm", null);

        verify(teamRepository, never()).save(any());
        verify(pointsTotalsCache, never()).set(any(), anyInt());
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

        // X (own total moved) and A (picked X) are in the envelope; B and C are not.
        Map<UUID, Object> perTeam = capturedRivalUpdates();
        assertEquals(java.util.Set.of(teamId, a.getId()), perTeam.keySet());

        // A's slice carries only X, with X's fresh total (100 + 50 = 150).
        // The total is passed explicitly (changedTeamTotal) so no extra cache read is needed.
        List<Map<String, Object>> rivals = (List<Map<String, Object>>) perTeam.get(a.getId());
        assertEquals(1, rivals.size());
        assertEquals(teamId, rivals.getFirst().get("id"));
        assertEquals(150, rivals.getFirst().get("totalPoints"));
    }

    @Test
    void createEntry_afterCompletion_beforeReveal_broadcastsNothing() {
        // Game over but the admin has not revealed the standings yet — scores
        // stay hidden from players: no full ranking and no rival updates.
        team.setCheckedIn(true);
        when(teamRepository.findById(teamId)).thenReturn(Optional.of(team));
        when(gameService.findById(gameId)).thenReturn(Optional.of(game(Game.GameStatus.COMPLETED)));

        ledger.createEntry(teamId, gameId, PointEntryType.CHECKPOINT_VERIFICATION,
            50, null, "Checkpoint X", null, null);
        fireAfterCommit();

        verify(webSocketPublisher, never()).broadcastToGame(eq(gameId),
            eq(WebSocketMessageType.RANKING_UPDATE.getValue()), any());
        verify(webSocketPublisher, never()).sendToTeam(any(), any(),
            eq(WebSocketMessageType.RIVAL_UPDATE.getValue()), any());
    }

    @Test
    void createEntry_afterReveal_broadcastsFullRanking_andNoPerTeamSends() {
        // Once published, a points correction re-broadcasts the updated ranking.
        team.setCheckedIn(true);
        when(teamRepository.findById(teamId)).thenReturn(Optional.of(team));
        Game published = game(Game.GameStatus.COMPLETED);
        published.setRankingsPublished(true);
        when(gameService.findById(gameId)).thenReturn(Optional.of(published));
        List<RankingResponse> full = List.of(new RankingResponse(1, teamId, "Test team", null, 150, false, null, false, false));
        when(rankingService.buildRanking(eq(gameId), any())).thenReturn(full);

        ledger.createEntry(teamId, gameId, PointEntryType.CHECKPOINT_VERIFICATION,
            50, null, "Checkpoint X", null, null);
        fireAfterCommit();

        verify(webSocketPublisher).broadcastToGame(eq(gameId),
            eq(WebSocketMessageType.RANKING_UPDATE.getValue()), eq(full));
        verify(webSocketPublisher, never()).sendToTeam(any(), any(),
            eq(WebSocketMessageType.RIVAL_UPDATE.getValue()), any());
    }

    @Test
    @SuppressWarnings("unchecked")
    void publishStandingsForTeams_coalesced_readsTotalsFromCache_andFansOutToAffected() {
        // The buffered accrual path publishes for a set of changed teams and reads every total
        // from the cache (each accrual wrote through post-commit), rather than being handed one
        // explicit total. Affected recipients = a changed team itself, or any team rivalling one.
        team.setCheckedIn(true); // X — the changed team
        Team a = teamInGame("A", List.of(teamId), true);   // picked X -> notified
        Team b = teamInGame("B", List.of(UUID.randomUUID()), true); // unrelated -> not notified
        when(gameService.findById(gameId)).thenReturn(Optional.of(game(Game.GameStatus.ACTIVE)));
        when(teamRepository.findByGameId(gameId)).thenReturn(List.of(team, a, b));
        when(pointsTotalsCache.get(teamId)).thenReturn(150);

        ledger.publishStandingsForTeams(gameId, java.util.Set.of(teamId));

        verify(webSocketPublisher, never()).broadcastToGame(eq(gameId),
            eq(WebSocketMessageType.RANKING_UPDATE.getValue()), any());

        Map<UUID, Object> perTeam = capturedRivalUpdates();
        assertEquals(java.util.Set.of(teamId, a.getId()), perTeam.keySet());
        assertFalse(perTeam.containsKey(b.getId()));

        // A's slice carries X read from the cache (150), proving no explicit total is needed.
        List<Map<String, Object>> rivals = (List<Map<String, Object>>) perTeam.get(a.getId());
        assertEquals(1, rivals.size());
        assertEquals(teamId, rivals.getFirst().get("id"));
        assertEquals(150, rivals.getFirst().get("totalPoints"));
    }

    @Test
    void publishStandingsForTeams_emptySet_isNoOp() {
        ledger.publishStandingsForTeams(gameId, java.util.Set.of());
        verify(webSocketPublisher, never()).broadcastToGame(any(), any(), any());
        verify(webSocketPublisher, never()).sendToTeam(any(), any(), any(), any());
        verify(webSocketPublisher, never()).publish(any());
    }

    // ---- admin ranking topic (always live, independent of publish state) --

    @Test
    void createEntry_duringGame_streamsFullRankingToAdminRankingTopic_evenBeforeReveal() {
        // Staff on the admin ranking page see the full live standings while the game
        // is still running, before any reveal — unlike players, who only get rival views.
        team.setCheckedIn(true);
        when(teamRepository.findById(teamId)).thenReturn(Optional.of(team));
        when(gameService.findById(gameId)).thenReturn(Optional.of(game(Game.GameStatus.ACTIVE)));
        when(teamRepository.findByGameId(gameId)).thenReturn(List.of(team));
        List<RankingResponse> full = List.of(new RankingResponse(1, teamId, "Test team", null, 150, false, null, false, false));
        when(rankingService.buildRanking(eq(gameId), any())).thenReturn(full);

        ledger.createEntry(teamId, gameId, PointEntryType.CHECKPOINT_VERIFICATION,
            50, null, "Checkpoint X", null, null);
        fireAfterCommit();

        // Full ranking goes to the admin ranking topic...
        assertEquals(full, capturedAdminRanking());
        // ...but is never broadcast to players while the game is running.
        verify(webSocketPublisher, never()).broadcastToGame(eq(gameId),
            eq(WebSocketMessageType.RANKING_UPDATE.getValue()), any());
    }

    @Test
    void createEntry_buildsRankingWithFreshTotalOverride_independentOfCacheWriteOrdering() {
        // The ranking payload must reflect the just-changed team's new total (100 + 50 = 150)
        // via an explicit override, so it is correct even if the separate post-commit cache
        // write (cacheTotalAfterCommit) has not run yet. Without the override, buildRanking
        // would read the stale cached 100 and the standings would not appear to change.
        team.setCheckedIn(true);
        when(teamRepository.findById(teamId)).thenReturn(Optional.of(team));
        when(gameService.findById(gameId)).thenReturn(Optional.of(game(Game.GameStatus.ACTIVE)));
        when(teamRepository.findByGameId(gameId)).thenReturn(List.of(team));

        ledger.createEntry(teamId, gameId, PointEntryType.MANUAL_ADJUSTMENT,
            50, null, "Bonus", null, null);
        fireAfterCommit();

        // The payload is lazy, so resolve the Supplier before asserting how it was built.
        capturedAdminRanking();
        verify(rankingService).buildRanking(gameId, Map.of(teamId, 150));
    }

    @Test
    void createEntry_afterReveal_streamsToAdminRankingTopic_inAdditionToPlayerBroadcast() {
        team.setCheckedIn(true);
        when(teamRepository.findById(teamId)).thenReturn(Optional.of(team));
        Game published = game(Game.GameStatus.COMPLETED);
        published.setRankingsPublished(true);
        when(gameService.findById(gameId)).thenReturn(Optional.of(published));
        List<RankingResponse> full = List.of(new RankingResponse(1, teamId, "Test team", null, 150, false, null, false, false));
        when(rankingService.buildRanking(eq(gameId), any())).thenReturn(full);

        ledger.createEntry(teamId, gameId, PointEntryType.CHECKPOINT_VERIFICATION,
            50, null, "Checkpoint X", null, null);
        fireAfterCommit();

        assertEquals(full, capturedAdminRanking());
        verify(webSocketPublisher).broadcastToGame(eq(gameId),
            eq(WebSocketMessageType.RANKING_UPDATE.getValue()), eq(full));
    }

    @Test
    void publishStandingsForTeams_coalesced_alsoStreamsToAdminRankingTopic() {
        team.setCheckedIn(true);
        when(gameService.findById(gameId)).thenReturn(Optional.of(game(Game.GameStatus.ACTIVE)));
        when(teamRepository.findByGameId(gameId)).thenReturn(List.of(team));
        List<RankingResponse> full = List.of(new RankingResponse(1, teamId, "Test team", null, 150, false, null, false, false));
        when(rankingService.buildRanking(eq(gameId), any())).thenReturn(full);

        ledger.publishStandingsForTeams(gameId, java.util.Set.of(teamId));

        assertEquals(full, capturedAdminRanking());
    }

    /**
     * The single multiplexed RIVAL_UPDATE envelope this call published, keyed by recipient team.
     * Rival views go out as one envelope per game rather than one publish per team, so
     * "who was notified" is now the envelope's key set.
     */
    private Map<UUID, Object> capturedRivalUpdates() {
        ArgumentCaptor<WebSocketMessage> captor = ArgumentCaptor.forClass(WebSocketMessage.class);
        verify(webSocketPublisher).publish(captor.capture());
        WebSocketMessage msg = captor.getValue();
        assertEquals(WebSocketMessageType.RIVAL_UPDATE.getValue(), msg.getType());
        assertEquals(gameId, msg.getTargetGameId());
        assertTrue(msg.isTeamOnly(), "rival views must never reach admin sessions");
        assertFalse(msg.isAdminOnly());
        return msg.getPerTeamPayloads();
    }

    /**
     * The ranking payload handed to the admin ranking topic. It is passed as a Supplier so it
     * is only built when someone is on the page, so the test has to resolve it.
     */
    private Object capturedAdminRanking() {
        @SuppressWarnings("unchecked")
        ArgumentCaptor<Supplier<Object>> captor = ArgumentCaptor.forClass(Supplier.class);
        verify(webSocketPublisher).sendToAdminTopicLazily(eq(gameId), eq(AdminTopics.RANKING),
            eq(WebSocketMessageType.RANKING_UPDATE.getValue()), captor.capture());
        return captor.getValue().get();
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
