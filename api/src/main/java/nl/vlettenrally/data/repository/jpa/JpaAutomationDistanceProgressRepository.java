package nl.vlettenrally.data.repository.jpa;

import io.quarkus.arc.profile.IfBuildProfile;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.transaction.Transactional;
import nl.vlettenrally.data.domain.AutomationDistanceProgress;
import nl.vlettenrally.data.entity.jpa.AutomationDistanceProgressEntity;
import nl.vlettenrally.data.mapper.AutomationDistanceProgressMapper;
import nl.vlettenrally.data.repository.AutomationDistanceProgressRepository;

import java.util.Optional;
import java.util.UUID;

/**
 * JPA implementation of AutomationDistanceProgressRepository.
 * Only active in prod profile - replaced by the mock in dev/test.
 *
 * Always reads the primary datasource: stale accrual state would
 * double-credit distance points.
 */
@IfBuildProfile("prod")
@ApplicationScoped
public class JpaAutomationDistanceProgressRepository extends ReadReplicaAwareRepository
        implements AutomationDistanceProgressRepository {

    @Override
    public Optional<AutomationDistanceProgress> findByRuleAndTeam(UUID ruleId, UUID teamId) {
        AutomationDistanceProgressEntity.Key key = new AutomationDistanceProgressEntity.Key();
        key.setRuleId(ruleId);
        key.setTeamId(teamId);
        return Optional.ofNullable(
                getReadEntityManager(true).find(AutomationDistanceProgressEntity.class, key))
                .map(AutomationDistanceProgressMapper::toDomain);
    }

    @Transactional
    @Override
    public AutomationDistanceProgress save(AutomationDistanceProgress progress) {
        AutomationDistanceProgressEntity.Key key = new AutomationDistanceProgressEntity.Key();
        key.setRuleId(progress.getRuleId());
        key.setTeamId(progress.getTeamId());

        AutomationDistanceProgressEntity existing =
                getWriteEntityManager().find(AutomationDistanceProgressEntity.class, key);
        if (existing != null) {
            AutomationDistanceProgressMapper.updateEntity(existing, progress);
            return AutomationDistanceProgressMapper.toDomain(existing);
        }

        AutomationDistanceProgressEntity entity = AutomationDistanceProgressMapper.toEntity(progress);
        getWriteEntityManager().persist(entity);
        return AutomationDistanceProgressMapper.toDomain(entity);
    }

    @Transactional
    @Override
    public void deleteByRuleAndTeam(UUID ruleId, UUID teamId) {
        getWriteEntityManager()
                .createQuery("DELETE FROM AutomationDistanceProgressEntity e "
                        + "WHERE e.ruleId = :ruleId AND e.teamId = :teamId")
                .setParameter("ruleId", ruleId)
                .setParameter("teamId", teamId)
                .executeUpdate();
    }
}
