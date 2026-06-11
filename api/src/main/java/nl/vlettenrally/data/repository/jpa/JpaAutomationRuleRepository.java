package nl.vlettenrally.data.repository.jpa;

import io.quarkus.arc.profile.IfBuildProfile;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.transaction.Transactional;
import nl.vlettenrally.data.domain.AutomationRule;
import nl.vlettenrally.data.entity.jpa.AutomationRuleEntity;
import nl.vlettenrally.data.entity.jpa.GameEntity;
import nl.vlettenrally.data.enums.AutomationTriggerType;
import nl.vlettenrally.data.mapper.AutomationRuleMapper;
import nl.vlettenrally.data.repository.AutomationRuleRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * JPA implementation of AutomationRuleRepository.
 * Only active in prod profile - replaced by MockAutomationRuleRepository in dev/test.
 */
@IfBuildProfile("prod")
@ApplicationScoped
public class JpaAutomationRuleRepository extends ReadReplicaAwareRepository implements AutomationRuleRepository {

    @Override
    public Optional<AutomationRule> findById(UUID id) {
        return Optional.ofNullable(getReadEntityManager(true).find(AutomationRuleEntity.class, id))
                .map(AutomationRuleMapper::toDomain);
    }

    @Override
    public List<AutomationRule> findByGameId(UUID gameId) {
        return getReadEntityManager()
                .createQuery("SELECT r FROM AutomationRuleEntity r WHERE r.game.id = :gameId "
                        + "ORDER BY r.createdAt DESC", AutomationRuleEntity.class)
                .setParameter("gameId", gameId)
                .getResultList().stream()
                .map(AutomationRuleMapper::toDomain)
                .toList();
    }

    @Override
    public List<AutomationRule> findByGameIdAndTriggerType(UUID gameId, AutomationTriggerType triggerType) {
        // Primary read: the engine's rule lookups feed dedup/accrual decisions.
        return getReadEntityManager(true)
                .createQuery("SELECT r FROM AutomationRuleEntity r WHERE r.game.id = :gameId "
                        + "AND r.triggerType = :triggerType", AutomationRuleEntity.class)
                .setParameter("gameId", gameId)
                .setParameter("triggerType", triggerType)
                .getResultList().stream()
                .map(AutomationRuleMapper::toDomain)
                .toList();
    }

    @Override
    public List<AutomationRule> findEnabledByTriggerType(AutomationTriggerType triggerType) {
        return getReadEntityManager(true)
                .createQuery("SELECT r FROM AutomationRuleEntity r WHERE r.triggerType = :triggerType "
                        + "AND r.enabled = true", AutomationRuleEntity.class)
                .setParameter("triggerType", triggerType)
                .getResultList().stream()
                .map(AutomationRuleMapper::toDomain)
                .toList();
    }

    @Transactional
    @Override
    public AutomationRule save(AutomationRule rule) {
        AutomationRuleEntity existing = rule.getId() != null
                ? getWriteEntityManager().find(AutomationRuleEntity.class, rule.getId())
                : null;

        if (existing != null) {
            AutomationRuleMapper.updateEntity(existing, rule);
            return AutomationRuleMapper.toDomain(existing);
        }

        AutomationRuleEntity entity = AutomationRuleMapper.toEntity(rule);
        if (rule.getGameId() != null) {
            entity.setGame(getWriteEntityManager().getReference(GameEntity.class, rule.getGameId()));
        }
        getWriteEntityManager().persist(entity);
        return AutomationRuleMapper.toDomain(entity);
    }

    @Transactional
    @Override
    public void delete(UUID id) {
        getWriteEntityManager().createQuery("DELETE FROM AutomationRuleEntity r WHERE r.id = :id")
                .setParameter("id", id)
                .executeUpdate();
    }
}
