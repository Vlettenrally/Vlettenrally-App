package nl.vlettenrally.api.v1;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import jakarta.ws.rs.BadRequestException;
import jakarta.ws.rs.NotFoundException;
import jakarta.ws.rs.core.Response;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;

import nl.vlettenrally.data.converter.AutomationConverter;
import nl.vlettenrally.data.domain.AutomationExecution;
import nl.vlettenrally.data.domain.AutomationRule;
import nl.vlettenrally.data.domain.automation.AutomationAction;
import nl.vlettenrally.data.domain.automation.AutomationTriggerConfig;
import nl.vlettenrally.data.dto.automation.AutomationExecutionListResponse;
import nl.vlettenrally.data.dto.automation.AutomationRuleRequest;
import nl.vlettenrally.data.dto.automation.AutomationRuleResponse;
import nl.vlettenrally.data.dto.automation.CopyAutomationsRequest;
import nl.vlettenrally.data.dto.automation.CopyAutomationsResult;
import nl.vlettenrally.data.dto.common.ErrorResponse;
import nl.vlettenrally.data.enums.AutomationExecutionStatus;
import nl.vlettenrally.data.enums.AutomationTriggerType;
import nl.vlettenrally.data.enums.MessageType;
import nl.vlettenrally.data.enums.AutomationActionType;
import nl.vlettenrally.data.repository.TeamRepository;
import nl.vlettenrally.service.AutomationService;

/**
 * Unit tests for AutomationResource: status codes for the happy paths and
 * for the system-rule / cross-game guard failures raised by the service.
 */
@ExtendWith(MockitoExtension.class)
class AutomationResourceTest {

    @Mock AutomationService automationService;
    @Mock TeamRepository teamRepository;
    @Spy AutomationConverter automationConverter = new AutomationConverter();

    @InjectMocks AutomationResource resource;

    private UUID gameId;
    private UUID ruleId;
    private AutomationRule rule;

    @BeforeEach
    void setUp() {
        gameId = UUID.randomUUID();

        rule = new AutomationRule();
        rule.setGameId(gameId);
        rule.setName("Felicitatie");
        rule.setTriggerType(AutomationTriggerType.CHECKPOINT_ENTRY);
        rule.setTriggerConfig(new AutomationTriggerConfig(null, null, null, null, null, null, null, null, null));
        rule.setActions(List.of(new AutomationAction(
            AutomationActionType.SEND_MESSAGE, "Goed bezig!", MessageType.GENERAL, null, null, null, null, null)));
        ruleId = rule.getId();

        lenient().when(automationService.lastFiredAt(any())).thenReturn(Map.of());
    }

    private AutomationRuleRequest request() {
        return new AutomationRuleRequest("Felicitatie", true, AutomationTriggerType.CHECKPOINT_ENTRY,
            new AutomationTriggerConfig(null, null, null, null, null, null, null, null, null),
            List.of(new AutomationAction(
                AutomationActionType.SEND_MESSAGE, "Goed bezig!", MessageType.GENERAL, null, null, null, null, null)),
            null, true, null);
    }

    @Test
    void listRules_returnsRulesWithMetadata() {
        when(automationService.listRules(gameId)).thenReturn(List.of(rule));
        when(automationService.executionCount(ruleId)).thenReturn(3L);


        Response response = resource.listRules(gameId);

        assertEquals(200, response.getStatus());
        @SuppressWarnings("unchecked")
        List<AutomationRuleResponse> body = (List<AutomationRuleResponse>) response.getEntity();
        assertEquals(1, body.size());
        assertEquals(3L, body.getFirst().executionCount());
        assertEquals("CHECKPOINT_ENTRY", body.getFirst().triggerType());
    }

    @Test
    void createRule_returns201() {
        when(automationService.createRule(eq(gameId), any(AutomationRule.class), any()))
            .thenReturn(rule);

        Response response = resource.createRule(gameId, request(), null);

        assertEquals(201, response.getStatus());
        AutomationRuleResponse body = (AutomationRuleResponse) response.getEntity();
        assertEquals(ruleId, body.id());
    }

    @Test
    void createRule_distanceTrigger_returns400() {
        when(automationService.createRule(eq(gameId), any(AutomationRule.class), any()))
            .thenThrow(new BadRequestException("Distance rules cannot be created by hand"));

        Response response = resource.createRule(gameId, request(), null);

        assertEquals(400, response.getStatus());
        assertEquals(ErrorResponse.class, response.getEntity().getClass());
    }

    @Test
    void copyRules_returns200WithResult() {
        UUID sourceGameId = UUID.randomUUID();
        CopyAutomationsResult result = new CopyAutomationsResult(3, List.of("Dup"), List.of(), true);
        when(automationService.copyRules(gameId, sourceGameId, null)).thenReturn(result);

        Response response = resource.copyRules(gameId, new CopyAutomationsRequest(sourceGameId), null);

        assertEquals(200, response.getStatus());
        CopyAutomationsResult body = (CopyAutomationsResult) response.getEntity();
        assertEquals(3, body.copied());
        assertTrue(body.distanceRateCopied());
    }

    @Test
    void copyRules_missingSource_returns400() {
        Response response = resource.copyRules(gameId, new CopyAutomationsRequest(null), null);

        assertEquals(400, response.getStatus());
        assertEquals(ErrorResponse.class, response.getEntity().getClass());
    }

    @Test
    void copyRules_sameGame_returns400() {
        UUID sourceGameId = UUID.randomUUID();
        when(automationService.copyRules(gameId, sourceGameId, null))
            .thenThrow(new BadRequestException("Cannot copy automations from a game into itself"));

        Response response = resource.copyRules(gameId, new CopyAutomationsRequest(sourceGameId), null);

        assertEquals(400, response.getStatus());
    }

    @Test
    void getRule_crossGame_returns404() {
        when(automationService.getRule(gameId, ruleId))
            .thenThrow(new NotFoundException("Automation rule not found"));

        Response response = resource.getRule(gameId, ruleId);

        assertEquals(404, response.getStatus());
    }

    @Test
    void deleteRule_systemRule_returns400() {
        org.mockito.Mockito.doThrow(new BadRequestException("System rules cannot be deleted"))
            .when(automationService).deleteRule(gameId, ruleId);

        Response response = resource.deleteRule(gameId, ruleId);

        assertEquals(400, response.getStatus());
    }

    @Test
    void deleteRule_normalRule_returns204() {
        Response response = resource.deleteRule(gameId, ruleId);

        assertEquals(204, response.getStatus());
    }

    @Test
    void listExecutions_resolvesTeamNames() {
        AutomationExecution execution = new AutomationExecution();
        execution.setRuleId(ruleId);
        execution.setGameId(gameId);
        execution.setStatus(AutomationExecutionStatus.SUCCESS);
        nl.vlettenrally.data.domain.Team team = new nl.vlettenrally.data.domain.Team();
        team.setId(UUID.randomUUID());
        team.setGameId(gameId);
        team.setName("De Watervlooien");
        execution.setTeamId(team.getId());

        when(automationService.listExecutions(eq(gameId), eq(ruleId), anyInt(), anyInt()))
            .thenReturn(List.of(execution));
        when(automationService.countExecutions(gameId, ruleId)).thenReturn(1L);
        when(teamRepository.findByGameId(gameId)).thenReturn(List.of(team));

        Response response = resource.listExecutions(gameId, ruleId, 0, 25);

        assertEquals(200, response.getStatus());
        AutomationExecutionListResponse body = (AutomationExecutionListResponse) response.getEntity();
        assertEquals(1, body.executions().size());
        assertEquals("De Watervlooien", body.executions().getFirst().teamName());
        assertEquals(1L, body.totalCount());
    }
}
