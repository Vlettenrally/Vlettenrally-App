package nl.vlettenrally.data.repository.mock;

import io.quarkus.arc.profile.IfBuildProfile;
import jakarta.enterprise.context.ApplicationScoped;
import nl.vlettenrally.data.domain.AutomationExecution;
import nl.vlettenrally.data.repository.AutomationExecutionRepository;

import java.time.Instant;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Mock implementation of AutomationExecutionRepository (starts empty).
 * Only active in dev and test profiles - replaced by JpaAutomationExecutionRepository in prod.
 */
@IfBuildProfile(anyOf = {"dev", "test"})
@ApplicationScoped
public class MockAutomationExecutionRepository implements AutomationExecutionRepository {

    private final Map<UUID, AutomationExecution> executions = new ConcurrentHashMap<>();

    @Override
    public boolean existsByRuleTeamAndContext(UUID ruleId, UUID teamId, String contextKey) {
        return executions.values().stream().anyMatch(e -> matches(e, ruleId, teamId, contextKey));
    }

    @Override
    public Optional<Instant> findLatestFiredAt(UUID ruleId, UUID teamId, String contextKey) {
        return executions.values().stream()
            .filter(e -> matches(e, ruleId, teamId, contextKey))
            .map(AutomationExecution::getFiredAt)
            .max(Comparator.naturalOrder());
    }

    private static boolean matches(AutomationExecution e, UUID ruleId, UUID teamId, String contextKey) {
        return ruleId.equals(e.getRuleId())
            && Objects.equals(teamId, e.getTeamId())
            && Objects.equals(contextKey, e.getContextKey());
    }

    @Override
    public List<AutomationExecution> findByRuleId(UUID ruleId, int page, int pageSize) {
        return executions.values().stream()
            .filter(e -> ruleId.equals(e.getRuleId()))
            .sorted(Comparator.comparing(AutomationExecution::getFiredAt).reversed())
            .skip((long) page * pageSize)
            .limit(pageSize)
            .toList();
    }

    @Override
    public long countByRuleId(UUID ruleId) {
        return executions.values().stream()
            .filter(e -> ruleId.equals(e.getRuleId()))
            .count();
    }

    @Override
    public Map<UUID, Instant> findLatestFiredAtByRuleIds(Set<UUID> ruleIds) {
        Map<UUID, Instant> result = new HashMap<>();
        for (AutomationExecution e : executions.values()) {
            if (ruleIds.contains(e.getRuleId())) {
                result.merge(e.getRuleId(), e.getFiredAt(),
                    (a, b) -> a.isAfter(b) ? a : b);
            }
        }
        return result;
    }

    @Override
    public AutomationExecution save(AutomationExecution execution) {
        executions.put(execution.getId(), execution);
        return execution;
    }
}
