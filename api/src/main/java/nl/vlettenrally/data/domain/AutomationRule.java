package nl.vlettenrally.data.domain;

import lombok.Getter;
import lombok.Setter;
import nl.vlettenrally.data.domain.automation.AutomationAction;
import nl.vlettenrally.data.domain.automation.AutomationTriggerConfig;
import nl.vlettenrally.data.enums.AutomationTriggerType;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Domain model for an automation rule: when {@code triggerType} fires (as
 * configured by {@code triggerConfig}), execute {@code actions} for the
 * targeted teams.
 *
 * {@code system} rules (the per-game "Afstandspunten" distance rule) cannot be
 * deleted and their trigger type cannot change; admins may rename, re-rate and
 * enable/disable them.
 *
 * {@code targetTeamIds} empty = rule applies to all teams of the game.
 * {@code fireOnce} = at most one execution per (rule, team, context key);
 * otherwise {@code cooldownMinutes} throttles re-fires.
 */
@Getter
@Setter
public class AutomationRule {
    private UUID id;
    private UUID gameId;
    private String name;
    private boolean enabled = true;
    private boolean system;
    private AutomationTriggerType triggerType;
    private AutomationTriggerConfig triggerConfig;
    private List<AutomationAction> actions = new ArrayList<>();
    private List<UUID> targetTeamIds = new ArrayList<>();
    private boolean fireOnce = true;
    private Integer cooldownMinutes;        // nullable; only meaningful when fireOnce = false
    private UUID createdBy;                 // nullable; null for system rules
    private Instant createdAt;
    private Instant updatedAt;

    public AutomationRule() {
        this.id = UUID.randomUUID();
        this.createdAt = Instant.now();
        this.updatedAt = this.createdAt;
    }

    /** Whether the rule applies to the given team ({@code targetTeamIds} empty = all teams). */
    public boolean appliesToTeam(UUID teamId) {
        return targetTeamIds == null || targetTeamIds.isEmpty() || targetTeamIds.contains(teamId);
    }
}
