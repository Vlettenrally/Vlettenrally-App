package nl.vlettenrally.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import nl.vlettenrally.data.domain.AutomationRule;
import nl.vlettenrally.data.domain.AdminNotification;
import nl.vlettenrally.data.domain.MessageTemplate;
import nl.vlettenrally.data.domain.Team;
import nl.vlettenrally.data.domain.automation.AutomationAction;
import nl.vlettenrally.data.enums.AutomationExecutionStatus;
import nl.vlettenrally.data.enums.AutomationTriggerType;
import nl.vlettenrally.data.enums.MessageType;
import nl.vlettenrally.data.enums.PointEntryType;
import nl.vlettenrally.data.domain.Game;
import nl.vlettenrally.data.repository.TeamRepository;
import nl.vlettenrally.websockets.WebSocketPublisher;

/**
 * Unit tests for AutomationActionExecutor: template resolution, firing-context
 * placeholders ({checkpoint}/{rival}; {team} is left for the message service),
 * batch sending, and SUCCESS/PARTIAL/FAILED aggregation.
 */
@ExtendWith(MockitoExtension.class)
class AutomationActionExecutorTest {

    @Mock MessageService messageService;
    @Mock PointsLedgerService pointsLedgerService;
    @Mock WebSocketPublisher webSocketPublisher;
    @Mock MessageTemplateService messageTemplateService;
    @Mock AdminInboxService adminInboxService;
    @Mock TeamRepository teamRepository;
    @Mock GameService gameService;
    @Mock RankingService rankingService;

    @InjectMocks AutomationActionExecutor executor;

    private static final AutomationActionExecutor.ActionContext EMPTY =
        AutomationActionExecutor.ActionContext.EMPTY;

    private UUID gameId;
    private AutomationRule rule;
    private Team team;

    @BeforeEach
    void setUp() {
        gameId = UUID.randomUUID();

        rule = new AutomationRule();
        rule.setGameId(gameId);
        rule.setName("Testregel");
        rule.setTriggerType(AutomationTriggerType.CHECKPOINT_ENTRY);

        team = new Team();
        team.setId(UUID.randomUUID());
        team.setGameId(gameId);
        team.setName("De Watervlooien");

    }

    private static AutomationAction message(String content, MessageType type, String icon) {
        return new AutomationAction.SendMessage(content, type, icon, null);
    }

    private static AutomationAction templateMessage(UUID templateId, String fallback) {
        return new AutomationAction.SendMessage(fallback, null, null, templateId);
    }

    private static AutomationAction points(int amount, String description) {
        return new AutomationAction.AwardPoints(amount, description);
    }

    private static AutomationAction notify(String text) {
        return new AutomationAction.NotifyAdmins(text);
    }

    private static AutomationAction finish() {
        return new AutomationAction.FinishTeam();
    }

    private static AutomationAction setRankingsPublished(boolean publish) {
        return new AutomationAction.SetRankingsPublished(publish);
    }

    private static AutomationAction setPublicRankingsEnabled(boolean enable) {
        return new AutomationAction.SetPublicRankingsEnabled(enable);
    }

    @Test
    void sendMessage_resolvesCheckpoint_leavesTeamForMessageService_andBatchesAllTargets() {
        Team second = new Team();
        second.setId(UUID.randomUUID());
        second.setName("Windkracht Tien");
        rule.setActions(List.of(message("Goed bezig {team} bij {checkpoint}!", null, "🎉")));

        AutomationActionExecutor.Result result = executor.execute(
            rule, List.of(team, second), new AutomationActionExecutor.ActionContext("Finish", null));

        assertEquals(AutomationExecutionStatus.SUCCESS, result.status());
        // {checkpoint} resolved now; {team} left for MessageService; one batch to all targets.
        verify(messageService).sendAutomationMessage(eq(gameId), eq(List.of(team.getId(), second.getId())),
            eq(MessageType.GENERAL), eq("Goed bezig {team} bij Finish!"), eq("🎉"), eq(rule.getId()));
    }

    @Test
    void sendMessage_usesTemplateContentTypeIcon_whenTemplateIdSet() {
        UUID templateId = UUID.randomUUID();
        when(messageTemplateService.findById(templateId)).thenReturn(Optional.of(
            new MessageTemplate(templateId, "Welkom", MessageType.CHECKPOINT, "Welkom {team}!", "👋")));
        rule.setActions(List.of(templateMessage(templateId, null)));

        AutomationActionExecutor.Result result = executor.execute(rule, List.of(team), EMPTY);

        assertEquals(AutomationExecutionStatus.SUCCESS, result.status());
        verify(messageService).sendAutomationMessage(eq(gameId), eq(List.of(team.getId())),
            eq(MessageType.CHECKPOINT), eq("Welkom {team}!"), eq("👋"), eq(rule.getId()));
    }

    @Test
    void sendMessage_fallsBackToActionText_whenTemplateMissing() {
        UUID templateId = UUID.randomUUID();
        when(messageTemplateService.findById(templateId)).thenReturn(Optional.empty());
        rule.setActions(List.of(templateMessage(templateId, "Reservetekst")));

        AutomationActionExecutor.Result result = executor.execute(rule, List.of(team), EMPTY);

        assertEquals(AutomationExecutionStatus.SUCCESS, result.status());
        verify(messageService).sendAutomationMessage(eq(gameId), eq(List.of(team.getId())),
            eq(MessageType.GENERAL), eq("Reservetekst"), isNull(), eq(rule.getId()));
    }

    @Test
    void sendMessage_failsAction_whenTemplateMissingAndNoFallback() {
        UUID templateId = UUID.randomUUID();
        when(messageTemplateService.findById(templateId)).thenReturn(Optional.empty());
        rule.setActions(List.of(templateMessage(templateId, null)));

        AutomationActionExecutor.Result result = executor.execute(rule, List.of(team), EMPTY);

        assertEquals(AutomationExecutionStatus.FAILED, result.status());
    }

    @Test
    void awardPoints_createsAutomationBonusEntry_withoutAdmin() {
        rule.setActions(List.of(points(50, null)));

        AutomationActionExecutor.Result result = executor.execute(rule, List.of(team), EMPTY);

        assertEquals(AutomationExecutionStatus.SUCCESS, result.status());
        verify(pointsLedgerService).createEntry(eq(team.getId()), eq(gameId),
            eq(PointEntryType.AUTOMATION_BONUS), eq(50), isNull(),
            eq("Automation: Testregel"), isNull(), isNull());
    }

    @Test
    void notifyAdmins_resolvesTeamAndRival() {
        rule.setActions(List.of(notify("{team} ging {rival} voorbij")));
        AdminNotification notification = new AdminNotification();
        notification.setId(UUID.randomUUID());
        notification.setGameId(gameId);
        when(adminInboxService.createNotification(any(), any(), anyString(), anyString(), anyString()))
            .thenReturn(notification);

        AutomationActionExecutor.Result result = executor.execute(
            rule, List.of(team), new AutomationActionExecutor.ActionContext(null, "Windkracht Tien"));

        assertEquals(AutomationExecutionStatus.SUCCESS, result.status());
        @SuppressWarnings("unchecked")
        ArgumentCaptor<Map<String, Object>> captor = ArgumentCaptor.forClass(Map.class);
        verify(webSocketPublisher).sendToAdmins(eq(gameId), eq("AUTOMATION_NOTIFICATION"), captor.capture());
        assertEquals("De Watervlooien ging Windkracht Tien voorbij", captor.getValue().get("text"));
        assertEquals("Testregel", captor.getValue().get("ruleName"));
        verify(adminInboxService).createNotification(eq(gameId), eq(team.getId()), eq("AUTOMATION_NOTIFICATION"),
            eq("De Watervlooien ging Windkracht Tien voorbij"), anyString());
    }

    @Test
    void finishTeam_marksTargetFinished() {
        rule.setActions(List.of(finish()));
        when(teamRepository.findById(team.getId())).thenReturn(Optional.of(team));

        AutomationActionExecutor.Result result = executor.execute(rule, List.of(team), EMPTY);

        assertEquals(AutomationExecutionStatus.SUCCESS, result.status());
        ArgumentCaptor<Team> captor = ArgumentCaptor.forClass(Team.class);
        verify(teamRepository).save(captor.capture());
        assertTrue(captor.getValue().isFinished());
        // The team's own dashboard is notified live (scoped to that team).
        verify(webSocketPublisher).sendToTeam(eq(gameId), eq(team.getId()), eq("TEAM_UPDATE"), any());
    }

    @Test
    void finishTeam_alreadyFinished_isNoOp() {
        team.setFinished(true);
        rule.setActions(List.of(finish()));
        when(teamRepository.findById(team.getId())).thenReturn(Optional.of(team));

        AutomationActionExecutor.Result result = executor.execute(rule, List.of(team), EMPTY);

        assertEquals(AutomationExecutionStatus.SUCCESS, result.status());
        verify(teamRepository, never()).save(any());
    }

    @Test
    void mixedActions_oneFailing_yieldsPartial() {
        rule.setActions(List.of(message("hoi", MessageType.GENERAL, null), points(50, "Bonus")));
        when(pointsLedgerService.createEntry(any(), any(), any(), anyInt(), any(), anyString(), any(), any()))
            .thenThrow(new RuntimeException("negative total"));

        AutomationActionExecutor.Result result = executor.execute(rule, List.of(team), EMPTY);

        assertEquals(AutomationExecutionStatus.PARTIAL, result.status());
        assertTrue(result.detail().contains("negative total"));
        verify(messageService).sendAutomationMessage(any(), anyList(), any(), anyString(), isNull(), any());
    }

    @Test
    void allActionsFailing_yieldsFailed() {
        rule.setActions(List.of(points(50, "Bonus")));
        when(pointsLedgerService.createEntry(any(), any(), any(), anyInt(), any(), anyString(), any(), any()))
            .thenThrow(new RuntimeException("boom"));

        AutomationActionExecutor.Result result = executor.execute(rule, List.of(team), EMPTY);

        assertEquals(AutomationExecutionStatus.FAILED, result.status());
    }

    @Test
    void setRankingsPublished_true_savesGameAndBroadcastsRanking() {
        rule.setActions(List.of(setRankingsPublished(true)));
        Game game = new Game();
        game.setId(gameId);
        when(gameService.findById(gameId)).thenReturn(Optional.of(game));
        when(rankingService.buildRanking(gameId)).thenReturn(List.of());

        AutomationActionExecutor.Result result = executor.execute(rule, List.of(), EMPTY);

        assertEquals(AutomationExecutionStatus.SUCCESS, result.status());
        ArgumentCaptor<Game> captor = ArgumentCaptor.forClass(Game.class);
        verify(gameService).save(captor.capture());
        assertTrue(captor.getValue().getRankingsPublished());
        verify(webSocketPublisher).broadcastToGame(eq(gameId), eq("RANKING_UPDATE"), any());
    }

    @Test
    void setPublicRankingsEnabled_true_savesGame() {
        rule.setActions(List.of(setPublicRankingsEnabled(true)));
        Game game = new Game();
        game.setId(gameId);
        when(gameService.findById(gameId)).thenReturn(Optional.of(game));

        AutomationActionExecutor.Result result = executor.execute(rule, List.of(), EMPTY);

        assertEquals(AutomationExecutionStatus.SUCCESS, result.status());
        ArgumentCaptor<Game> captor = ArgumentCaptor.forClass(Game.class);
        verify(gameService).save(captor.capture());
        assertTrue(captor.getValue().getPublicRankingsEnabled());
        verify(webSocketPublisher, never()).broadcastToGame(any(), any(), any());
    }

    @Test
    void setPublicRankingsEnabled_false_savesGame() {
        rule.setActions(List.of(setPublicRankingsEnabled(false)));
        Game game = new Game();
        game.setId(gameId);
        game.setPublicRankingsEnabled(true);
        when(gameService.findById(gameId)).thenReturn(Optional.of(game));

        AutomationActionExecutor.Result result = executor.execute(rule, List.of(), EMPTY);

        assertEquals(AutomationExecutionStatus.SUCCESS, result.status());
        ArgumentCaptor<Game> captor = ArgumentCaptor.forClass(Game.class);
        verify(gameService).save(captor.capture());
        assertTrue(!captor.getValue().getPublicRankingsEnabled());
    }

    @Test
    void setRankingsPublished_false_savesGameAndDoesNotBroadcast() {
        rule.setActions(List.of(setRankingsPublished(false)));
        Game game = new Game();
        game.setId(gameId);
        game.setRankingsPublished(true);
        when(gameService.findById(gameId)).thenReturn(Optional.of(game));

        AutomationActionExecutor.Result result = executor.execute(rule, List.of(), EMPTY);

        assertEquals(AutomationExecutionStatus.SUCCESS, result.status());
        ArgumentCaptor<Game> captor = ArgumentCaptor.forClass(Game.class);
        verify(gameService).save(captor.capture());
        assertTrue(!captor.getValue().getRankingsPublished());
        verify(webSocketPublisher, never()).broadcastToGame(any(), any(), any());
    }

    @Test
    void awardPoints_partialTeamFailure_keepsActionSuccessful() {
        Team second = new Team();
        second.setId(UUID.randomUUID());
        second.setGameId(gameId);
        second.setName("Windkracht Tien");

        rule.setActions(List.of(points(-50, "Aftrek")));
        when(pointsLedgerService.createEntry(eq(team.getId()), any(), any(), anyInt(), any(), anyString(), any(), any()))
            .thenThrow(new RuntimeException("negative total"));

        AutomationActionExecutor.Result result = executor.execute(rule, List.of(team, second), EMPTY);

        // One of two teams failed → the action (and execution) still counts as ok overall.
        assertEquals(AutomationExecutionStatus.SUCCESS, result.status());
        assertTrue(result.detail().contains("negative total"));
        verify(pointsLedgerService).createEntry(eq(second.getId()), any(), any(), anyInt(), any(), anyString(), any(), any());
    }
}
