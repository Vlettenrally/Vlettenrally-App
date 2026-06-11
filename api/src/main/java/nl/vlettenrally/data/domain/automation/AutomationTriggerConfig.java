package nl.vlettenrally.data.domain.automation;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.quarkus.runtime.annotations.RegisterForReflection;
import nl.vlettenrally.data.domain.Game;

import java.time.Instant;
import java.util.UUID;

/**
 * Trigger configuration for an automation rule. One flat record covers all
 * trigger types (avoids Jackson polymorphism, which would need extra native
 * image setup); which fields are required depends on the rule's trigger type
 * and is enforced by AutomationService validation:
 *
 * <ul>
 *   <li>CHECKPOINT_ENTRY — {@code checkpointId} (null = any checkpoint).
 *       Optionally {@code comparison} + the time fields below restrict firing
 *       to passes BEFORE/AFTER a boundary time (e.g. finish-zone bonus for
 *       arriving before a deadline, or a penalty for arriving after); the
 *       boundary uses the same ABSOLUTE/RELATIVE shape as SCHEDULED_TIME and
 *       is compared against the recorded pass time.</li>
 *   <li>SCHEDULED_TIME — {@code mode}; ABSOLUTE needs {@code absoluteTime},
 *       RELATIVE needs {@code relativeTo} + {@code offsetMinutes}</li>
 *   <li>GAME_STATUS_CHANGE — {@code toStatus} (ACTIVE or COMPLETED)</li>
 *   <li>TEAM_INACTIVITY — {@code inactiveMinutes}</li>
 *   <li>RIVAL_OVERTAKE — no config</li>
 *   <li>DISTANCE_TRAVELED — {@code pointsPer100m} (points credited per whole
 *       100 m traveled)</li>
 * </ul>
 */
@RegisterForReflection
@JsonInclude(JsonInclude.Include.NON_NULL)
public record AutomationTriggerConfig(
    UUID checkpointId,
    ScheduleMode mode,
    Instant absoluteTime,
    RelativeAnchor relativeTo,
    Integer offsetMinutes,
    Game.GameStatus toStatus,
    Integer inactiveMinutes,
    Integer pointsPer100m,
    PassComparison comparison
) {
    public enum ScheduleMode { ABSOLUTE, RELATIVE }

    public enum RelativeAnchor { GAME_START, GAME_END }

    /** For CHECKPOINT_ENTRY: only fire when the pass is before/after the boundary time. */
    public enum PassComparison { BEFORE, AFTER }
}
