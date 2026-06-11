package nl.vlettenrally.data.repository;

import nl.vlettenrally.data.domain.AutomationDistanceProgress;

import java.util.Optional;
import java.util.UUID;

/**
 * Repository interface for per (distance rule, team) accrual state.
 * Reads must come from the primary datasource — a stale replica read would
 * double-credit distance points.
 */
public interface AutomationDistanceProgressRepository {

    Optional<AutomationDistanceProgress> findByRuleAndTeam(UUID ruleId, UUID teamId);

    AutomationDistanceProgress save(AutomationDistanceProgress progress);
}
