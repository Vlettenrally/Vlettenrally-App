package nl.vlettenrally.data.repository;

import nl.vlettenrally.data.domain.AutomationExecution;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * Repository interface for automation executions (audit log + dedup state).
 *
 * Dedup lookups treat {@code teamId} and {@code contextKey} as exact matches,
 * including null (a game-level firing has both null).
 * Implementations must read these from the primary datasource — a stale
 * replica read would re-fire a fire-once rule.
 */
public interface AutomationExecutionRepository {

    boolean existsByRuleTeamAndContext(UUID ruleId, UUID teamId, String contextKey);

    Optional<Instant> findLatestFiredAt(UUID ruleId, UUID teamId, String contextKey);

    /** Executions of a rule, newest first. Pagination is zero-based. */
    List<AutomationExecution> findByRuleId(UUID ruleId, int page, int pageSize);

    long countByRuleId(UUID ruleId);

    /** Latest firedAt per rule, for the rules-list "last fired" column. Missing key = never fired. */
    Map<UUID, Instant> findLatestFiredAtByRuleIds(Set<UUID> ruleIds);

    AutomationExecution save(AutomationExecution execution);
}
