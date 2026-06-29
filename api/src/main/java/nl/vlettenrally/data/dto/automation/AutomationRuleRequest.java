package nl.vlettenrally.data.dto.automation;

import io.quarkus.runtime.annotations.RegisterForReflection;
import nl.vlettenrally.data.domain.automation.AutomationAction;
import nl.vlettenrally.data.domain.automation.AutomationTriggerConfig;
import nl.vlettenrally.data.enums.AutomationTriggerType;

import java.util.List;
import java.util.UUID;

/**
 * Create/update payload for an automation rule. The trigger config and actions
 * reuse the flat domain records directly — they are plain Jackson-friendly
 * value objects with no persistence concerns.
 */
@RegisterForReflection
public record AutomationRuleRequest(
    String name,
    Boolean enabled,
    AutomationTriggerType triggerType,
    AutomationTriggerConfig triggerConfig,
    List<AutomationAction> actions,
    List<UUID> targetTeamIds,
    Boolean fireOnce,
    Integer cooldownMinutes,
    Boolean triggerForFinishedTeams
) {}
