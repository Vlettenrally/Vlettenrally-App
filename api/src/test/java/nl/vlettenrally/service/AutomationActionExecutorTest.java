package nl.vlettenrally.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import nl.vlettenrally.data.domain.AutomationRule;
import nl.vlettenrally.data.domain.Team;
import nl.vlettenrally.data.domain.automation.AutomationAction;
import nl.vlettenrally.data.enums.AutomationActionType;
import nl.vlettenrally.data.enums.AutomationExecutionStatus;
import nl.vlettenrally.data.enums.AutomationTriggerType;
import nl.vlettenrally.data.enums.MessageType;
import nl.vlettenrally.data.enums.PointEntryType;
import nl.vlettenrally.websockets.WebSocketPublisher;

/**
 * Unit tests for AutomationActionExecutor: dispatch to the right services,
 * {team} placeholder substitution, and SUCCESS/PARTIAL/FAILED aggregation.
 */
@ExtendWith(MockitoExtension.class)
class AutomationActionExecutorTest {

    @Mock MessageService messageService;
    @Mock PointsLedgerService pointsLedgerService;
    @Mock WebSocketPublisher webSocketPublisher;

    @InjectMocks AutomationActionExecutor executor;

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
        return new AutomationAction(AutomationActionType.SEND_MESSAGE, content, type, icon, null, null, null);
    }

    private static AutomationAction points(int amount, String description) {
        return new AutomationAction(AutomationActionType.AWARD_POINTS, null, null, null, amount, description, null);
    }

    private static AutomationAction notify(String text) {
        return new AutomationAction(AutomationActionType.NOTIFY_ADMINS, null, null, null, null, null, text);
    }

    @Test
    void sendMessage_substitutesTeamPlaceholder_andDefaultsToGeneral() {
        rule.setActions(List.of(message("Goed bezig {team}!", null, "🎉")));

        AutomationActionExecutor.Result result = executor.execute(rule, List.of(team));

        assertEquals(AutomationExecutionStatus.SUCCESS, result.status());
        verify(messageService).sendAutomationMessage(eq(gameId), eq(List.of(team.getId())),
            eq(MessageType.GENERAL), eq("Goed bezig De Watervlooien!"), eq("🎉"), eq(rule.getId()));
    }

    @Test
    void awardPoints_createsAutomationBonusEntry_withoutAdmin() {
        rule.setActions(List.of(points(50, null)));

        AutomationActionExecutor.Result result = executor.execute(rule, List.of(team));

        assertEquals(AutomationExecutionStatus.SUCCESS, result.status());
        verify(pointsLedgerService).createEntry(eq(team.getId()), eq(gameId),
            eq(PointEntryType.AUTOMATION_BONUS), eq(50), isNull(),
            eq("Automation: Testregel"), isNull(), isNull());
    }

    @Test
    void notifyAdmins_sendsSingleNotification_withSubstitution() {
        rule.setActions(List.of(notify("{team} heeft een melding")));

        AutomationActionExecutor.Result result = executor.execute(rule, List.of(team));

        assertEquals(AutomationExecutionStatus.SUCCESS, result.status());
        @SuppressWarnings("unchecked")
        ArgumentCaptor<Map<String, Object>> captor = ArgumentCaptor.forClass(Map.class);
        verify(webSocketPublisher).sendToAdmins(eq(gameId), eq("AUTOMATION_NOTIFICATION"), captor.capture());
        assertEquals("De Watervlooien heeft een melding", captor.getValue().get("text"));
        assertEquals("Testregel", captor.getValue().get("ruleName"));
    }

    @Test
    void mixedActions_oneFailing_yieldsPartial() {
        rule.setActions(List.of(message("hoi", MessageType.GENERAL, null), points(50, "Bonus")));
        when(pointsLedgerService.createEntry(any(), any(), any(), anyInt(), any(), anyString(), any(), any()))
            .thenThrow(new RuntimeException("negative total"));

        AutomationActionExecutor.Result result = executor.execute(rule, List.of(team));

        assertEquals(AutomationExecutionStatus.PARTIAL, result.status());
        assertTrue(result.detail().contains("negative total"));
        verify(messageService).sendAutomationMessage(any(), anyList(), any(), anyString(), isNull(), any());
    }

    @Test
    void allActionsFailing_yieldsFailed() {
        rule.setActions(List.of(points(50, "Bonus")));
        when(pointsLedgerService.createEntry(any(), any(), any(), anyInt(), any(), anyString(), any(), any()))
            .thenThrow(new RuntimeException("boom"));

        AutomationActionExecutor.Result result = executor.execute(rule, List.of(team));

        assertEquals(AutomationExecutionStatus.FAILED, result.status());
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

        AutomationActionExecutor.Result result = executor.execute(rule, List.of(team, second));

        // One of two teams failed → the action (and execution) still counts as ok overall.
        assertEquals(AutomationExecutionStatus.SUCCESS, result.status());
        assertTrue(result.detail().contains("negative total"));
        verify(pointsLedgerService).createEntry(eq(second.getId()), any(), any(), anyInt(), any(), anyString(), any(), any());
    }
}
