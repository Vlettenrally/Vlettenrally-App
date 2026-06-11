package nl.vlettenrally.data.dto.automation;

import io.quarkus.runtime.annotations.RegisterForReflection;
import nl.vlettenrally.data.domain.automation.AutomationAction;
import nl.vlettenrally.data.domain.automation.AutomationTriggerConfig;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@RegisterForReflection
public record AutomationRuleResponse(
    UUID id,
    UUID gameId,
    String name,
    boolean enabled,
    boolean system,
    String triggerType,
    AutomationTriggerConfig triggerConfig,
    List<AutomationAction> actions,
    List<UUID> targetTeamIds,
    boolean fireOnce,
    Integer cooldownMinutes,
    Instant createdAt,
    Instant updatedAt,
    Instant lastFiredAt,
    long executionCount
) {}
