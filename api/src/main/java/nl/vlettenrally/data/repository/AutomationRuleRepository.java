package nl.vlettenrally.data.repository;

import nl.vlettenrally.data.domain.AutomationRule;
import nl.vlettenrally.data.enums.AutomationTriggerType;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Repository interface for automation rules.
 */
public interface AutomationRuleRepository {

    Optional<AutomationRule> findById(UUID id);

    /** All rules of a game, newest first. */
    List<AutomationRule> findByGameId(UUID gameId);

    /**
     * Rules of a game with the given trigger type, including disabled ones —
     * the distance engine keeps baselines up to date even while disabled.
     */
    List<AutomationRule> findByGameIdAndTriggerType(UUID gameId, AutomationTriggerType triggerType);

    /** Enabled rules with the given trigger type across all games (scheduler use). */
    List<AutomationRule> findEnabledByTriggerType(AutomationTriggerType triggerType);

    /**
     * Count rules created by an admin. Used to decide whether that admin can be
     * deleted outright without silently dropping the authorship trail.
     */
    long countByCreatedBy(UUID adminId);

    AutomationRule save(AutomationRule rule);

    void delete(UUID id);
}
