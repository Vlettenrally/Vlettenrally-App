package nl.vlettenrally.data.domain;

import lombok.Getter;
import lombok.Setter;
import nl.vlettenrally.data.enums.AutomationExecutionStatus;

import java.time.Instant;
import java.util.UUID;

/**
 * One firing of an automation rule. Doubles as the admin-facing audit log and
 * as dedup state: fire-once rules skip when a row exists for the same
 * (rule, team, contextKey); cooldown rules skip while the latest matching row
 * is younger than the cooldown.
 */
@Getter
@Setter
public class AutomationExecution {
    private UUID id;
    private UUID ruleId;
    private UUID gameId;
    private UUID teamId;            // nullable; null for game-level firings
    private String contextKey;      // nullable; checkpoint id, "PLANNED->ACTIVE", overtaken rival id
    private AutomationExecutionStatus status;
    private String detail;          // nullable; JSON with per-action results / errors
    private Instant firedAt;

    public AutomationExecution() {
        this.id = UUID.randomUUID();
        this.firedAt = Instant.now();
    }
}
