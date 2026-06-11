package nl.vlettenrally.data.repository.jpa;

import io.quarkus.arc.profile.IfBuildProfile;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.persistence.Tuple;
import jakarta.persistence.TypedQuery;
import jakarta.transaction.Transactional;
import nl.vlettenrally.data.domain.AutomationExecution;
import nl.vlettenrally.data.entity.jpa.AutomationExecutionEntity;
import nl.vlettenrally.data.entity.jpa.AutomationRuleEntity;
import nl.vlettenrally.data.mapper.AutomationExecutionMapper;
import nl.vlettenrally.data.repository.AutomationExecutionRepository;

import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * JPA implementation of AutomationExecutionRepository.
 * Only active in prod profile - replaced by MockAutomationExecutionRepository in dev/test.
 *
 * Dedup lookups always read the primary datasource: a stale replica read
 * would re-fire a fire-once rule.
 */
@IfBuildProfile("prod")
@ApplicationScoped
public class JpaAutomationExecutionRepository extends ReadReplicaAwareRepository
        implements AutomationExecutionRepository {

    @Override
    public boolean existsByRuleTeamAndContext(UUID ruleId, UUID teamId, String contextKey) {
        return dedupQuery("SELECT COUNT(e) FROM AutomationExecutionEntity e", ruleId, teamId, contextKey, Long.class)
                .getSingleResult() > 0;
    }

    @Override
    public Optional<Instant> findLatestFiredAt(UUID ruleId, UUID teamId, String contextKey) {
        return Optional.ofNullable(
                dedupQuery("SELECT MAX(e.firedAt) FROM AutomationExecutionEntity e",
                        ruleId, teamId, contextKey, Instant.class)
                .getSingleResult());
    }

    private <T> TypedQuery<T> dedupQuery(String select, UUID ruleId, UUID teamId, String contextKey,
                                         Class<T> resultType) {
        StringBuilder jpql = new StringBuilder(select).append(" WHERE e.rule.id = :ruleId");
        jpql.append(teamId != null ? " AND e.teamId = :teamId" : " AND e.teamId IS NULL");
        jpql.append(contextKey != null ? " AND e.contextKey = :contextKey" : " AND e.contextKey IS NULL");

        TypedQuery<T> query = getReadEntityManager(true)
                .createQuery(jpql.toString(), resultType)
                .setParameter("ruleId", ruleId);
        if (teamId != null)     query.setParameter("teamId", teamId);
        if (contextKey != null) query.setParameter("contextKey", contextKey);
        return query;
    }

    @Override
    public List<AutomationExecution> findByRuleId(UUID ruleId, int page, int pageSize) {
        return getReadEntityManager()
                .createQuery("SELECT e FROM AutomationExecutionEntity e WHERE e.rule.id = :ruleId "
                        + "ORDER BY e.firedAt DESC", AutomationExecutionEntity.class)
                .setParameter("ruleId", ruleId)
                .setFirstResult(page * pageSize)
                .setMaxResults(pageSize)
                .getResultList().stream()
                .map(AutomationExecutionMapper::toDomain)
                .toList();
    }

    @Override
    public long countByRuleId(UUID ruleId) {
        return getReadEntityManager()
                .createQuery("SELECT COUNT(e) FROM AutomationExecutionEntity e WHERE e.rule.id = :ruleId",
                        Long.class)
                .setParameter("ruleId", ruleId)
                .getSingleResult();
    }

    @Override
    public Map<UUID, Instant> findLatestFiredAtByRuleIds(Set<UUID> ruleIds) {
        if (ruleIds == null || ruleIds.isEmpty()) {
            return Map.of();
        }
        List<Tuple> rows = getReadEntityManager()
                .createQuery("SELECT e.rule.id AS ruleId, MAX(e.firedAt) AS firedAt "
                        + "FROM AutomationExecutionEntity e WHERE e.rule.id IN :ruleIds "
                        + "GROUP BY e.rule.id", Tuple.class)
                .setParameter("ruleIds", ruleIds)
                .getResultList();

        Map<UUID, Instant> result = new HashMap<>();
        for (Tuple row : rows) {
            result.put(row.get("ruleId", UUID.class), row.get("firedAt", Instant.class));
        }
        return result;
    }

    @Transactional
    @Override
    public AutomationExecution save(AutomationExecution execution) {
        AutomationExecutionEntity entity = AutomationExecutionMapper.toEntity(execution);
        if (execution.getRuleId() != null) {
            entity.setRule(getWriteEntityManager().getReference(AutomationRuleEntity.class, execution.getRuleId()));
        }
        getWriteEntityManager().persist(entity);
        return AutomationExecutionMapper.toDomain(entity);
    }
}
