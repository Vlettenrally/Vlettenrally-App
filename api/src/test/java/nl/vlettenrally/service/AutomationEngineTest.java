package nl.vlettenrally.service;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import nl.vlettenrally.data.domain.AutomationDistanceProgress;
import nl.vlettenrally.data.domain.AutomationExecution;
import nl.vlettenrally.data.domain.AutomationRule;
import nl.vlettenrally.data.domain.Game;
import nl.vlettenrally.data.domain.LocationData;
import nl.vlettenrally.data.domain.PointEntry;
import nl.vlettenrally.data.domain.Team;
import nl.vlettenrally.data.domain.automation.AutomationTriggerConfig;
import nl.vlettenrally.data.enums.AutomationExecutionStatus;
import nl.vlettenrally.data.enums.AutomationTriggerType;
import nl.vlettenrally.data.enums.PointEntryType;
import nl.vlettenrally.data.repository.AutomationDistanceProgressRepository;
import nl.vlettenrally.data.repository.AutomationExecutionRepository;
import nl.vlettenrally.data.repository.AutomationRuleRepository;
import nl.vlettenrally.data.repository.CheckpointRepository;
import nl.vlettenrally.data.repository.LocationDataRepository;
import nl.vlettenrally.data.repository.TeamRepository;
import nl.vlettenrally.websockets.WebSocketPublisher;

/**
 * Unit tests for AutomationEngine: trigger matching, dedup (fire-once and
 * cooldown), overtake math, distance accrual, time/inactivity evaluation,
 * the re-entrancy guard and exception safety of the hooks.
 */
@ExtendWith(MockitoExtension.class)
class AutomationEngineTest {

    @Mock AutomationRuleRepository ruleRepository;
    @Mock AutomationExecutionRepository executionRepository;
    @Mock AutomationDistanceProgressRepository distanceProgressRepository;
    @Mock TeamRepository teamRepository;
    @Mock CheckpointRepository checkpointRepository;
    @Mock GameService gameService;
    @Mock LocationDataRepository locationDataRepository;
    @Mock PointsLedgerService pointsLedgerService;
    @Mock AutomationActionExecutor actionExecutor;
    @Mock WebSocketPublisher webSocketPublisher;

    @InjectMocks AutomationEngine engine;

    private UUID gameId;
    private UUID teamId;
    private UUID checkpointId;
    private Team team;

    private static final AutomationActionExecutor.Result OK =
        new AutomationActionExecutor.Result(AutomationExecutionStatus.SUCCESS, "[]");

    @BeforeEach
    void setUp() {
        gameId = UUID.randomUUID();
        teamId = UUID.randomUUID();
        checkpointId = UUID.randomUUID();

        team = new Team();
        team.setId(teamId);
        team.setGameId(gameId);
        team.setName("De Watervlooien");
        team.setCheckedIn(true);

        lenient().when(teamRepository.findById(teamId)).thenReturn(Optional.of(team));
        lenient().when(actionExecutor.execute(any(), anyList(), any())).thenReturn(OK);
    }

    private AutomationRule rule(AutomationTriggerType type, AutomationTriggerConfig config) {
        AutomationRule rule = new AutomationRule();
        rule.setGameId(gameId);
        rule.setName("Test rule");
        rule.setTriggerType(type);
        rule.setTriggerConfig(config);
        return rule;
    }

    private static AutomationTriggerConfig emptyConfig() {
        return new AutomationTriggerConfig(null, null, null, null, null, null, null, null, null);
    }

    // ==================== Checkpoint trigger ====================

    @Test
    void checkpointPass_firesMatchingRule_andRecordsExecution() {
        AutomationRule rule = rule(AutomationTriggerType.CHECKPOINT_ENTRY, emptyConfig());
        when(ruleRepository.findByGameIdAndTriggerType(gameId, AutomationTriggerType.CHECKPOINT_ENTRY))
            .thenReturn(List.of(rule));
        when(executionRepository.existsByRuleTeamAndContext(rule.getId(), teamId, checkpointId.toString()))
            .thenReturn(false);

        engine.onCheckpointPass(gameId, teamId, checkpointId, Instant.now());

        verify(actionExecutor).execute(eq(rule), eq(List.of(team)), any());
        ArgumentCaptor<AutomationExecution> captor = ArgumentCaptor.forClass(AutomationExecution.class);
        verify(executionRepository).save(captor.capture());
        assertEquals(rule.getId(), captor.getValue().getRuleId());
        assertEquals(teamId, captor.getValue().getTeamId());
        assertEquals(checkpointId.toString(), captor.getValue().getContextKey());
        assertEquals(AutomationExecutionStatus.SUCCESS, captor.getValue().getStatus());
        verify(webSocketPublisher).sendToAdmins(eq(gameId), eq("AUTOMATION_TRIGGERED"), any());
    }

    @Test
    void checkpointPass_skipsDisabledRule() {
        AutomationRule rule = rule(AutomationTriggerType.CHECKPOINT_ENTRY, emptyConfig());
        rule.setEnabled(false);
        when(ruleRepository.findByGameIdAndTriggerType(gameId, AutomationTriggerType.CHECKPOINT_ENTRY))
            .thenReturn(List.of(rule));

        engine.onCheckpointPass(gameId, teamId, checkpointId, Instant.now());

        verifyNoInteractions(actionExecutor);
        verify(executionRepository, never()).save(any());
    }

    @Test
    void checkpointPass_skipsNonTargetedTeam() {
        AutomationRule rule = rule(AutomationTriggerType.CHECKPOINT_ENTRY, emptyConfig());
        rule.setTargetTeamIds(List.of(UUID.randomUUID()));
        when(ruleRepository.findByGameIdAndTriggerType(gameId, AutomationTriggerType.CHECKPOINT_ENTRY))
            .thenReturn(List.of(rule));

        engine.onCheckpointPass(gameId, teamId, checkpointId, Instant.now());

        verifyNoInteractions(actionExecutor);
    }

    @Test
    void checkpointPass_skipsOtherCheckpoint_whenRuleIsCheckpointSpecific() {
        AutomationRule rule = rule(AutomationTriggerType.CHECKPOINT_ENTRY,
            new AutomationTriggerConfig(UUID.randomUUID(), null, null, null, null, null, null, null, null));
        when(ruleRepository.findByGameIdAndTriggerType(gameId, AutomationTriggerType.CHECKPOINT_ENTRY))
            .thenReturn(List.of(rule));

        engine.onCheckpointPass(gameId, teamId, checkpointId, Instant.now());

        verifyNoInteractions(actionExecutor);
    }

    @Test
    void checkpointPass_fireOnce_skipsSecondPass_butFiresOtherCheckpoint() {
        AutomationRule rule = rule(AutomationTriggerType.CHECKPOINT_ENTRY, emptyConfig());
        when(ruleRepository.findByGameIdAndTriggerType(gameId, AutomationTriggerType.CHECKPOINT_ENTRY))
            .thenReturn(List.of(rule));
        UUID otherCheckpoint = UUID.randomUUID();
        when(executionRepository.existsByRuleTeamAndContext(rule.getId(), teamId, checkpointId.toString()))
            .thenReturn(true);
        when(executionRepository.existsByRuleTeamAndContext(rule.getId(), teamId, otherCheckpoint.toString()))
            .thenReturn(false);

        engine.onCheckpointPass(gameId, teamId, checkpointId, Instant.now());
        engine.onCheckpointPass(gameId, teamId, otherCheckpoint, Instant.now());

        verify(actionExecutor, times(1)).execute(eq(rule), anyList(), any());
        ArgumentCaptor<AutomationExecution> captor = ArgumentCaptor.forClass(AutomationExecution.class);
        verify(executionRepository).save(captor.capture());
        assertEquals(otherCheckpoint.toString(), captor.getValue().getContextKey());
    }

    @Test
    void checkpointPass_neverThrows_whenRepositoryFails() {
        when(ruleRepository.findByGameIdAndTriggerType(any(), any()))
            .thenThrow(new RuntimeException("boom"));

        assertDoesNotThrow(() -> engine.onCheckpointPass(gameId, teamId, checkpointId, Instant.now()));
    }

    // ==================== Checkpoint time window (finish bonus/penalty) ====================

    private AutomationRule windowRule(AutomationTriggerConfig.PassComparison comparison,
                                      AutomationTriggerConfig.ScheduleMode mode,
                                      Instant absoluteTime,
                                      AutomationTriggerConfig.RelativeAnchor relativeTo,
                                      Integer offsetMinutes) {
        AutomationRule rule = rule(AutomationTriggerType.CHECKPOINT_ENTRY,
            new AutomationTriggerConfig(null, mode, absoluteTime, relativeTo, offsetMinutes,
                null, null, null, comparison));
        when(ruleRepository.findByGameIdAndTriggerType(gameId, AutomationTriggerType.CHECKPOINT_ENTRY))
            .thenReturn(List.of(rule));
        return rule;
    }

    @Test
    void checkpointPass_beforeWindow_firesOnlyForEarlyPass() {
        Instant deadline = Instant.parse("2026-06-13T17:00:00Z");
        Game game = new Game();
        game.setId(gameId);
        when(gameService.findById(gameId)).thenReturn(Optional.of(game));
        AutomationRule rule = windowRule(AutomationTriggerConfig.PassComparison.BEFORE,
            AutomationTriggerConfig.ScheduleMode.ABSOLUTE, deadline, null, null);
        lenient().when(executionRepository.existsByRuleTeamAndContext(any(), any(), anyString()))
            .thenReturn(false);

        // Too late: no bonus.
        engine.onCheckpointPass(gameId, teamId, checkpointId, deadline.plus(Duration.ofMinutes(5)));
        verifyNoInteractions(actionExecutor);

        // In time: bonus fires.
        engine.onCheckpointPass(gameId, teamId, checkpointId, deadline.minus(Duration.ofMinutes(5)));
        verify(actionExecutor).execute(eq(rule), eq(List.of(team)), any());
    }

    @Test
    void checkpointPass_afterWindow_relativeToGameEnd_firesForLatePass() {
        Instant gameEnd = Instant.parse("2026-06-13T17:00:00Z");
        Game game = new Game();
        game.setId(gameId);
        game.setEndTime(gameEnd);
        when(gameService.findById(gameId)).thenReturn(Optional.of(game));
        AutomationRule rule = windowRule(AutomationTriggerConfig.PassComparison.AFTER,
            AutomationTriggerConfig.ScheduleMode.RELATIVE, null,
            AutomationTriggerConfig.RelativeAnchor.GAME_END, 0);
        lenient().when(executionRepository.existsByRuleTeamAndContext(any(), any(), anyString()))
            .thenReturn(false);

        // Finished in time: no penalty.
        engine.onCheckpointPass(gameId, teamId, checkpointId, gameEnd.minus(Duration.ofMinutes(1)));
        verifyNoInteractions(actionExecutor);

        // Finished after the end time: penalty fires.
        engine.onCheckpointPass(gameId, teamId, checkpointId, gameEnd.plus(Duration.ofMinutes(1)));
        verify(actionExecutor).execute(eq(rule), eq(List.of(team)), any());
    }

    @Test
    void checkpointPass_windowWithUnresolvableBoundary_neverFires() {
        Game game = new Game();
        game.setId(gameId);
        // endTime not set — the relative boundary cannot be computed.
        when(gameService.findById(gameId)).thenReturn(Optional.of(game));
        windowRule(AutomationTriggerConfig.PassComparison.AFTER,
            AutomationTriggerConfig.ScheduleMode.RELATIVE, null,
            AutomationTriggerConfig.RelativeAnchor.GAME_END, 0);

        engine.onCheckpointPass(gameId, teamId, checkpointId, Instant.now());

        verifyNoInteractions(actionExecutor);
    }

    // ==================== Recursion guard ====================

    @Test
    void hooksAreNoOps_whileActionsAreExecuting() {
        AutomationRule rule = rule(AutomationTriggerType.CHECKPOINT_ENTRY, emptyConfig());
        when(ruleRepository.findByGameIdAndTriggerType(gameId, AutomationTriggerType.CHECKPOINT_ENTRY))
            .thenReturn(List.of(rule));
        when(executionRepository.existsByRuleTeamAndContext(any(), any(), any())).thenReturn(false);

        // The action calls back into the engine (e.g. AWARD_POINTS → ledger →
        // onTeamPointsChanged). The nested hook must be a no-op.
        when(actionExecutor.execute(any(), anyList(), any())).thenAnswer(invocation -> {
            engine.onCheckpointPass(gameId, teamId, UUID.randomUUID(), Instant.now());
            engine.onTeamPointsChanged(gameId, teamId, 0, 100);
            return OK;
        });

        engine.onCheckpointPass(gameId, teamId, checkpointId, Instant.now());

        verify(actionExecutor, times(1)).execute(any(), anyList(), any());
        verify(executionRepository, times(1)).save(any());
    }

    // ==================== Game status trigger ====================

    @Test
    void statusChange_firesRuleMatchingTargetStatus_gameLevel() {
        Game game = new Game();
        game.setId(gameId);
        AutomationRule rule = rule(AutomationTriggerType.GAME_STATUS_CHANGE,
            new AutomationTriggerConfig(null, null, null, null, null, Game.GameStatus.ACTIVE, null, null, null));
        when(ruleRepository.findByGameIdAndTriggerType(gameId, AutomationTriggerType.GAME_STATUS_CHANGE))
            .thenReturn(List.of(rule));
        when(executionRepository.existsByRuleTeamAndContext(rule.getId(), null, "PLANNED->ACTIVE"))
            .thenReturn(false);
        when(teamRepository.findByGameId(gameId)).thenReturn(List.of(team));

        engine.onGameStatusChange(game, Game.GameStatus.PLANNED, Game.GameStatus.ACTIVE);

        verify(actionExecutor).execute(eq(rule), eq(List.of(team)), any());
        ArgumentCaptor<AutomationExecution> captor = ArgumentCaptor.forClass(AutomationExecution.class);
        verify(executionRepository).save(captor.capture());
        assertNull(captor.getValue().getTeamId());
        assertEquals("PLANNED->ACTIVE", captor.getValue().getContextKey());
    }

    @Test
    void statusChange_skipsRuleForOtherStatus() {
        Game game = new Game();
        game.setId(gameId);
        AutomationRule rule = rule(AutomationTriggerType.GAME_STATUS_CHANGE,
            new AutomationTriggerConfig(null, null, null, null, null, Game.GameStatus.COMPLETED, null, null, null));
        when(ruleRepository.findByGameIdAndTriggerType(gameId, AutomationTriggerType.GAME_STATUS_CHANGE))
            .thenReturn(List.of(rule));

        engine.onGameStatusChange(game, Game.GameStatus.PLANNED, Game.GameStatus.ACTIVE);

        verifyNoInteractions(actionExecutor);
    }

    // ==================== Rival overtake trigger ====================

    private Team rivalTeam(int totalPoints) {
        Team rival = new Team();
        rival.setId(UUID.randomUUID());
        rival.setGameId(gameId);
        rival.setName("Rivaal");
        rival.setTotalPoints(totalPoints);
        when(teamRepository.findById(rival.getId())).thenReturn(Optional.of(rival));
        return rival;
    }

    @Test
    void overtake_fires_whenCrossingARivalsTotal() {
        Team rival = rivalTeam(150);
        team.getRivals().add(rival.getId());

        AutomationRule rule = rule(AutomationTriggerType.RIVAL_OVERTAKE, emptyConfig());
        when(ruleRepository.findByGameIdAndTriggerType(gameId, AutomationTriggerType.RIVAL_OVERTAKE))
            .thenReturn(List.of(rule));
        when(executionRepository.existsByRuleTeamAndContext(rule.getId(), teamId, rival.getId().toString()))
            .thenReturn(false);

        engine.onTeamPointsChanged(gameId, teamId, 140, 160);

        verify(actionExecutor).execute(eq(rule), eq(List.of(team)), any());
        ArgumentCaptor<AutomationExecution> captor = ArgumentCaptor.forClass(AutomationExecution.class);
        verify(executionRepository).save(captor.capture());
        assertEquals(rival.getId().toString(), captor.getValue().getContextKey());
    }

    @Test
    void overtake_doesNotFire_whenAlreadyAhead() {
        Team rival = rivalTeam(100);
        team.getRivals().add(rival.getId());

        AutomationRule rule = rule(AutomationTriggerType.RIVAL_OVERTAKE, emptyConfig());
        when(ruleRepository.findByGameIdAndTriggerType(gameId, AutomationTriggerType.RIVAL_OVERTAKE))
            .thenReturn(List.of(rule));

        engine.onTeamPointsChanged(gameId, teamId, 120, 140);

        verifyNoInteractions(actionExecutor);
    }

    @Test
    void overtake_doesNotFire_whenPointsDecrease() {
        engine.onTeamPointsChanged(gameId, teamId, 100, 80);

        verifyNoInteractions(ruleRepository, actionExecutor);
    }

    @Test
    void overtake_fires_fromTie() {
        Team rival = rivalTeam(100);
        team.getRivals().add(rival.getId());

        AutomationRule rule = rule(AutomationTriggerType.RIVAL_OVERTAKE, emptyConfig());
        when(ruleRepository.findByGameIdAndTriggerType(gameId, AutomationTriggerType.RIVAL_OVERTAKE))
            .thenReturn(List.of(rule));
        when(executionRepository.existsByRuleTeamAndContext(any(), any(), anyString())).thenReturn(false);

        engine.onTeamPointsChanged(gameId, teamId, 100, 101);

        verify(actionExecutor).execute(eq(rule), anyList(), any());
    }

    // ==================== Distance accrual ====================

    private AutomationRule distanceRule(int pointsPer100m) {
        AutomationRule rule = rule(AutomationTriggerType.DISTANCE_TRAVELED,
            new AutomationTriggerConfig(null, null, null, null, null, null, null, pointsPer100m, null));
        rule.setSystem(true);
        rule.setFireOnce(false);
        when(ruleRepository.findByGameIdAndTriggerType(gameId, AutomationTriggerType.DISTANCE_TRAVELED))
            .thenReturn(List.of(rule));
        return rule;
    }

    private AutomationDistanceProgress progress(AutomationRule rule, double lastReported,
                                                double leftover, int credited, UUID entryId) {
        AutomationDistanceProgress p = new AutomationDistanceProgress();
        p.setRuleId(rule.getId());
        p.setTeamId(teamId);
        p.setLastReportedMeters(lastReported);
        p.setLeftoverMeters(leftover);
        p.setCreditedPoints(credited);
        p.setPointEntryId(entryId);
        when(distanceProgressRepository.findByRuleAndTeam(rule.getId(), teamId))
            .thenReturn(Optional.of(p));
        return p;
    }

    @Test
    void distance_firstSight_createsBaselineWithoutCredit() {
        AutomationRule rule = distanceRule(1);
        when(distanceProgressRepository.findByRuleAndTeam(rule.getId(), teamId))
            .thenReturn(Optional.empty());

        engine.onDistanceUpdate(gameId, teamId, 5000);

        ArgumentCaptor<AutomationDistanceProgress> captor =
            ArgumentCaptor.forClass(AutomationDistanceProgress.class);
        verify(distanceProgressRepository).save(captor.capture());
        assertEquals(5000, captor.getValue().getLastReportedMeters());
        assertEquals(0, captor.getValue().getCreditedPoints());
        verifyNoInteractions(pointsLedgerService);
    }

    @Test
    void distance_creditsWholePoints_andCarriesRemainder() {
        AutomationRule rule = distanceRule(1);
        progress(rule, 1000, 30, 10, null);

        PointEntry entry = new PointEntry();
        when(pointsLedgerService.createEntry(eq(teamId), eq(gameId), eq(PointEntryType.DISTANCE_BONUS),
                anyInt(), isNull(), anyString(), isNull(), isNull())).thenReturn(entry);

        // +250 m → leftover 280 → 2 whole 100 m blocks, 80 m remainder
        engine.onDistanceUpdate(gameId, teamId, 1250);

        verify(pointsLedgerService).createEntry(eq(teamId), eq(gameId), eq(PointEntryType.DISTANCE_BONUS),
            eq(12), isNull(), eq("Afstandspunten"), isNull(), isNull());

        ArgumentCaptor<AutomationDistanceProgress> captor =
            ArgumentCaptor.forClass(AutomationDistanceProgress.class);
        verify(distanceProgressRepository).save(captor.capture());
        assertEquals(1250, captor.getValue().getLastReportedMeters());
        assertEquals(80, captor.getValue().getLeftoverMeters(), 0.0001);
        assertEquals(12, captor.getValue().getCreditedPoints());
        assertEquals(entry.getId(), captor.getValue().getPointEntryId());
    }

    @Test
    void distance_multiplePointsPer100m_creditsRateTimesBlocks() {
        AutomationRule rule = distanceRule(3);
        progress(rule, 1000, 0, 0, null);

        PointEntry entry = new PointEntry();
        when(pointsLedgerService.createEntry(eq(teamId), eq(gameId), eq(PointEntryType.DISTANCE_BONUS),
                anyInt(), isNull(), anyString(), isNull(), isNull())).thenReturn(entry);

        // +250 m → 2 whole blocks × 3 points = 6, 50 m remainder
        engine.onDistanceUpdate(gameId, teamId, 1250);

        verify(pointsLedgerService).createEntry(eq(teamId), eq(gameId), eq(PointEntryType.DISTANCE_BONUS),
            eq(6), isNull(), eq("Afstandspunten"), isNull(), isNull());

        ArgumentCaptor<AutomationDistanceProgress> captor =
            ArgumentCaptor.forClass(AutomationDistanceProgress.class);
        verify(distanceProgressRepository).save(captor.capture());
        assertEquals(50, captor.getValue().getLeftoverMeters(), 0.0001);
        assertEquals(6, captor.getValue().getCreditedPoints());
    }

    @Test
    void distance_accruesExistingEntry_onceCreated() {
        AutomationRule rule = distanceRule(1);
        UUID entryId = UUID.randomUUID();
        progress(rule, 1000, 0, 10, entryId);

        engine.onDistanceUpdate(gameId, teamId, 1100);

        verify(pointsLedgerService).accrueEntry(entryId, 11);
        verify(pointsLedgerService, never()).createEntry(any(), any(), any(), anyInt(), any(), any(), any(), any());
    }

    @Test
    void distance_belowThreshold_onlyAccumulatesLeftover() {
        AutomationRule rule = distanceRule(1);
        progress(rule, 1000, 20, 10, null);

        engine.onDistanceUpdate(gameId, teamId, 1050);

        verifyNoInteractions(pointsLedgerService);
        ArgumentCaptor<AutomationDistanceProgress> captor =
            ArgumentCaptor.forClass(AutomationDistanceProgress.class);
        verify(distanceProgressRepository).save(captor.capture());
        assertEquals(70, captor.getValue().getLeftoverMeters(), 0.0001);
    }

    @Test
    void distance_disabledRule_advancesBaselineWithoutCredit() {
        AutomationRule rule = distanceRule(1);
        rule.setEnabled(false);
        progress(rule, 1000, 0, 0, null);

        engine.onDistanceUpdate(gameId, teamId, 3000);

        verifyNoInteractions(pointsLedgerService);
        ArgumentCaptor<AutomationDistanceProgress> captor =
            ArgumentCaptor.forClass(AutomationDistanceProgress.class);
        verify(distanceProgressRepository).save(captor.capture());
        // Baseline moved: the 2000 m traveled while disabled can never be credited later.
        assertEquals(3000, captor.getValue().getLastReportedMeters());
        assertEquals(0, captor.getValue().getLeftoverMeters(), 0.0001);
    }

    @Test
    void distance_negativeDelta_rebaselinesWithoutCredit() {
        AutomationRule rule = distanceRule(1);
        progress(rule, 5000, 40, 50, null);

        engine.onDistanceUpdate(gameId, teamId, 1000);

        verifyNoInteractions(pointsLedgerService);
        ArgumentCaptor<AutomationDistanceProgress> captor =
            ArgumentCaptor.forClass(AutomationDistanceProgress.class);
        verify(distanceProgressRepository).save(captor.capture());
        assertEquals(1000, captor.getValue().getLastReportedMeters());
        assertEquals(40, captor.getValue().getLeftoverMeters(), 0.0001);
    }

    // ==================== Scheduled time trigger ====================

    private Game activeGame(Instant startSignal, Instant end) {
        Game game = new Game();
        game.setId(gameId);
        game.setStatus(Game.GameStatus.ACTIVE);
        game.setStartSignalTime(startSignal);
        game.setEndTime(end);
        lenient().when(gameService.findById(gameId)).thenReturn(Optional.of(game));
        return game;
    }

    @Test
    void scheduled_absolute_firesWhenDue() {
        Instant now = Instant.now();
        activeGame(null, null);
        AutomationRule rule = rule(AutomationTriggerType.SCHEDULED_TIME,
            new AutomationTriggerConfig(null, AutomationTriggerConfig.ScheduleMode.ABSOLUTE,
                now.minus(Duration.ofMinutes(1)), null, null, null, null, null, null));
        when(ruleRepository.findEnabledByTriggerType(AutomationTriggerType.SCHEDULED_TIME))
            .thenReturn(List.of(rule));
        when(ruleRepository.findEnabledByTriggerType(AutomationTriggerType.TEAM_INACTIVITY))
            .thenReturn(List.of());
        when(executionRepository.existsByRuleTeamAndContext(rule.getId(), null, null)).thenReturn(false);
        when(teamRepository.findByGameId(gameId)).thenReturn(List.of(team));

        engine.evaluateTimeAndInactivity(now);

        verify(actionExecutor).execute(eq(rule), eq(List.of(team)), any());
    }

    @Test
    void scheduled_absolute_doesNotFireBeforeDue() {
        Instant now = Instant.now();
        activeGame(null, null);
        AutomationRule rule = rule(AutomationTriggerType.SCHEDULED_TIME,
            new AutomationTriggerConfig(null, AutomationTriggerConfig.ScheduleMode.ABSOLUTE,
                now.plus(Duration.ofMinutes(10)), null, null, null, null, null, null));
        when(ruleRepository.findEnabledByTriggerType(AutomationTriggerType.SCHEDULED_TIME))
            .thenReturn(List.of(rule));
        when(ruleRepository.findEnabledByTriggerType(AutomationTriggerType.TEAM_INACTIVITY))
            .thenReturn(List.of());

        engine.evaluateTimeAndInactivity(now);

        verifyNoInteractions(actionExecutor);
    }

    @Test
    void scheduled_relativeToGameEnd_firesAtOffset() {
        Instant now = Instant.now();
        // "30 minutes before the end signal" — end is in 29 minutes, so due.
        activeGame(null, now.plus(Duration.ofMinutes(29)));
        AutomationRule rule = rule(AutomationTriggerType.SCHEDULED_TIME,
            new AutomationTriggerConfig(null, AutomationTriggerConfig.ScheduleMode.RELATIVE,
                null, AutomationTriggerConfig.RelativeAnchor.GAME_END, -30, null, null, null, null));
        when(ruleRepository.findEnabledByTriggerType(AutomationTriggerType.SCHEDULED_TIME))
            .thenReturn(List.of(rule));
        when(ruleRepository.findEnabledByTriggerType(AutomationTriggerType.TEAM_INACTIVITY))
            .thenReturn(List.of());
        when(executionRepository.existsByRuleTeamAndContext(rule.getId(), null, null)).thenReturn(false);
        when(teamRepository.findByGameId(gameId)).thenReturn(List.of(team));

        engine.evaluateTimeAndInactivity(now);

        verify(actionExecutor).execute(eq(rule), anyList(), any());
    }

    @Test
    void scheduled_staleRule_isSkipped() {
        Instant now = Instant.now();
        activeGame(null, null);
        AutomationRule rule = rule(AutomationTriggerType.SCHEDULED_TIME,
            new AutomationTriggerConfig(null, AutomationTriggerConfig.ScheduleMode.ABSOLUTE,
                now.minus(Duration.ofHours(25)), null, null, null, null, null, null));
        when(ruleRepository.findEnabledByTriggerType(AutomationTriggerType.SCHEDULED_TIME))
            .thenReturn(List.of(rule));
        when(ruleRepository.findEnabledByTriggerType(AutomationTriggerType.TEAM_INACTIVITY))
            .thenReturn(List.of());

        engine.evaluateTimeAndInactivity(now);

        verifyNoInteractions(actionExecutor);
    }

    @Test
    void scheduled_alreadyFired_isSkipped() {
        Instant now = Instant.now();
        activeGame(null, null);
        AutomationRule rule = rule(AutomationTriggerType.SCHEDULED_TIME,
            new AutomationTriggerConfig(null, AutomationTriggerConfig.ScheduleMode.ABSOLUTE,
                now.minus(Duration.ofMinutes(5)), null, null, null, null, null, null));
        when(ruleRepository.findEnabledByTriggerType(AutomationTriggerType.SCHEDULED_TIME))
            .thenReturn(List.of(rule));
        when(ruleRepository.findEnabledByTriggerType(AutomationTriggerType.TEAM_INACTIVITY))
            .thenReturn(List.of());
        when(executionRepository.existsByRuleTeamAndContext(rule.getId(), null, null)).thenReturn(true);

        engine.evaluateTimeAndInactivity(now);

        verifyNoInteractions(actionExecutor);
    }

    // ==================== Inactivity trigger ====================

    private AutomationRule inactivityRule(Integer cooldownMinutes) {
        AutomationRule rule = rule(AutomationTriggerType.TEAM_INACTIVITY,
            new AutomationTriggerConfig(null, null, null, null, null, null, 30, null, null));
        rule.setFireOnce(false);
        rule.setCooldownMinutes(cooldownMinutes);
        when(ruleRepository.findEnabledByTriggerType(AutomationTriggerType.TEAM_INACTIVITY))
            .thenReturn(List.of(rule));
        lenient().when(ruleRepository.findEnabledByTriggerType(AutomationTriggerType.SCHEDULED_TIME))
            .thenReturn(List.of());
        return rule;
    }

    private void lastLocationAt(Instant locationTime) {
        LocationData location = new LocationData();
        location.setLocationTime(locationTime);
        when(locationDataRepository.findLatestByTeamId(teamId)).thenReturn(Optional.of(location));
    }

    @Test
    void inactivity_fires_whenLatestLocationIsStale() {
        Instant now = Instant.now();
        activeGame(now.minus(Duration.ofHours(2)), null);
        AutomationRule rule = inactivityRule(60);
        when(teamRepository.findByGameId(gameId)).thenReturn(List.of(team));
        lastLocationAt(now.minus(Duration.ofMinutes(45)));
        when(executionRepository.findLatestFiredAt(rule.getId(), teamId, null))
            .thenReturn(Optional.empty());

        engine.evaluateTimeAndInactivity(now);

        verify(actionExecutor).execute(eq(rule), eq(List.of(team)), any());
    }

    @Test
    void inactivity_respectsCooldown() {
        Instant now = Instant.now();
        activeGame(now.minus(Duration.ofHours(2)), null);
        AutomationRule rule = inactivityRule(60);
        when(teamRepository.findByGameId(gameId)).thenReturn(List.of(team));
        lastLocationAt(now.minus(Duration.ofMinutes(45)));
        when(executionRepository.findLatestFiredAt(rule.getId(), teamId, null))
            .thenReturn(Optional.of(now.minus(Duration.ofMinutes(20))));

        engine.evaluateTimeAndInactivity(now);

        verifyNoInteractions(actionExecutor);
    }

    @Test
    void inactivity_doesNotFire_whenRecentLocationExists() {
        Instant now = Instant.now();
        activeGame(now.minus(Duration.ofHours(2)), null);
        inactivityRule(null);
        when(teamRepository.findByGameId(gameId)).thenReturn(List.of(team));
        lastLocationAt(now.minus(Duration.ofMinutes(5)));

        engine.evaluateTimeAndInactivity(now);

        verifyNoInteractions(actionExecutor);
    }

    @Test
    void inactivity_teamWithoutData_usesGameStartAsBaseline() {
        Instant now = Instant.now();
        // Game just started — a team with no data yet is not "inactive".
        activeGame(now.minus(Duration.ofMinutes(10)), null);
        inactivityRule(null);
        when(teamRepository.findByGameId(gameId)).thenReturn(List.of(team));
        when(locationDataRepository.findLatestByTeamId(teamId)).thenReturn(Optional.empty());

        engine.evaluateTimeAndInactivity(now);

        verifyNoInteractions(actionExecutor);
    }

    @Test
    void inactivity_skipsNonActiveGames() {
        Instant now = Instant.now();
        Game game = new Game();
        game.setId(gameId);
        game.setStatus(Game.GameStatus.PLANNED);
        when(gameService.findById(gameId)).thenReturn(Optional.of(game));
        inactivityRule(null);

        engine.evaluateTimeAndInactivity(now);

        verifyNoInteractions(actionExecutor, locationDataRepository);
    }
}
