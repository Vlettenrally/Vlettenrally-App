package nl.vlettenrally.data.converter;

import jakarta.enterprise.context.ApplicationScoped;
import nl.vlettenrally.data.domain.AutomationExecution;
import nl.vlettenrally.data.domain.AutomationRule;
import nl.vlettenrally.data.dto.automation.AutomationExecutionResponse;
import nl.vlettenrally.data.dto.automation.AutomationRuleRequest;
import nl.vlettenrally.data.dto.automation.AutomationRuleResponse;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

@ApplicationScoped
public class AutomationConverter {

    public AutomationRuleResponse toResponse(AutomationRule rule, Instant lastFiredAt, long executionCount) {
        if (rule == null) return null;
        return new AutomationRuleResponse(
            rule.getId(),
            rule.getGameId(),
            rule.getName(),
            rule.isEnabled(),
            rule.isSystem(),
            rule.getTriggerType() != null ? rule.getTriggerType().name() : null,
            rule.getTriggerConfig(),
            rule.getActions(),
            rule.getTargetTeamIds(),
            rule.isFireOnce(),
            rule.getCooldownMinutes(),
            rule.getCreatedAt(),
            rule.getUpdatedAt(),
            lastFiredAt,
            executionCount
        );
    }

    /** Build a domain draft from a request; the service validates and persists. */
    public AutomationRule toDomain(AutomationRuleRequest request) {
        AutomationRule rule = new AutomationRule();
        rule.setName(request.name());
        rule.setEnabled(request.enabled() == null || request.enabled());
        rule.setTriggerType(request.triggerType());
        rule.setTriggerConfig(request.triggerConfig());
        rule.setActions(request.actions() != null ? request.actions() : new ArrayList<>());
        rule.setTargetTeamIds(request.targetTeamIds() != null ? request.targetTeamIds() : new ArrayList<>());
        rule.setFireOnce(request.fireOnce() == null || request.fireOnce());
        rule.setCooldownMinutes(request.cooldownMinutes());
        return rule;
    }

    public AutomationExecutionResponse toExecutionResponse(AutomationExecution execution, String teamName) {
        if (execution == null) return null;
        return new AutomationExecutionResponse(
            execution.getId(),
            execution.getRuleId(),
            execution.getTeamId(),
            teamName,
            execution.getContextKey(),
            execution.getStatus() != null ? execution.getStatus().name() : null,
            execution.getDetail(),
            execution.getFiredAt()
        );
    }
}
