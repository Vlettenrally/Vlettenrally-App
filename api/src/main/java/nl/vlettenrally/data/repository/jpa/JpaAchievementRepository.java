package nl.vlettenrally.data.repository.jpa;

import io.quarkus.arc.profile.IfBuildProfile;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.persistence.NoResultException;
import jakarta.transaction.Transactional;
import nl.vlettenrally.data.domain.Achievement;
import nl.vlettenrally.data.entity.jpa.AchievementEntity;
import nl.vlettenrally.data.entity.jpa.AdminEntity;
import nl.vlettenrally.data.entity.jpa.CheckpointEntity;
import nl.vlettenrally.data.entity.jpa.TeamEntity;
import nl.vlettenrally.data.mapper.AchievementMapper;
import nl.vlettenrally.data.enums.AchievementStatus;
import nl.vlettenrally.data.repository.AchievementRepository;

import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * JPA implementation of AchievementRepository with read replica support.
 * Only active in prod profile - replaced by MockAchievementRepository in dev/test.
 */
@IfBuildProfile("prod")
@ApplicationScoped
public class JpaAchievementRepository extends ReadReplicaAwareRepository implements AchievementRepository {

    @Override
    public Optional<Achievement> findById(UUID id) {
        AchievementEntity entity = getReadEntityManager().find(AchievementEntity.class, id);
        return Optional.ofNullable(AchievementMapper.toDomain(entity));
    }

    @Override
    public List<Achievement> findAll() {
        List<AchievementEntity> entities = getReadEntityManager()
                .createQuery("SELECT a FROM AchievementEntity a ORDER BY a.detectedAt DESC", AchievementEntity.class)
                .getResultList();
        return entities.stream()
                .map(AchievementMapper::toDomain)
                .collect(Collectors.toList());
    }

    @Override
    public List<Achievement> findByTeamId(UUID teamId) {
        List<AchievementEntity> entities = getReadEntityManager()
                .createQuery("SELECT a FROM AchievementEntity a WHERE a.team.id = :teamId ORDER BY a.detectedAt DESC", AchievementEntity.class)
                .setParameter("teamId", teamId)
                .getResultList();
        return entities.stream()
                .map(AchievementMapper::toDomain)
                .collect(Collectors.toList());
    }

    @Override
    public List<Achievement> findByCheckpointId(UUID checkpointId) {
        List<AchievementEntity> entities = getReadEntityManager()
                .createQuery("SELECT a FROM AchievementEntity a WHERE a.checkpoint.id = :checkpointId ORDER BY a.detectedAt DESC", AchievementEntity.class)
                .setParameter("checkpointId", checkpointId)
                .getResultList();
        return entities.stream()
                .map(AchievementMapper::toDomain)
                .collect(Collectors.toList());
    }

    @Override
    public Optional<Achievement> findByTeamIdAndCheckpointId(UUID teamId, UUID checkpointId) {
        try {
            AchievementEntity entity = getReadEntityManager()
                    .createQuery("SELECT a FROM AchievementEntity a WHERE a.team.id = :teamId AND a.checkpoint.id = :checkpointId", AchievementEntity.class)
                    .setParameter("teamId", teamId)
                    .setParameter("checkpointId", checkpointId)
                    .getSingleResult();
            return Optional.ofNullable(AchievementMapper.toDomain(entity));
        } catch (NoResultException e) {
            return Optional.empty();
        }
    }

    @Override
    @Transactional
    public Achievement save(Achievement achievement) {
        AchievementEntity entity = getWriteEntityManager().find(AchievementEntity.class, achievement.getId());
        
        if (entity == null) {
            // New entity
            entity = AchievementMapper.toEntity(achievement);
            
            // Set team relationship
            if (achievement.getTeamId() != null) {
                TeamEntity team = getWriteEntityManager().find(TeamEntity.class, achievement.getTeamId());
                entity.setTeam(team);
            }
            
            // Set checkpoint relationship
            if (achievement.getCheckpointId() != null) {
                CheckpointEntity checkpoint = getWriteEntityManager().find(CheckpointEntity.class, achievement.getCheckpointId());
                entity.setCheckpoint(checkpoint);
            }
            
            // Set verifiedBy relationship
            if (achievement.getVerifiedBy() != null) {
                AdminEntity admin = getWriteEntityManager().find(AdminEntity.class, achievement.getVerifiedBy());
                entity.setVerifiedBy(admin);
            }
            
            getWriteEntityManager().persist(entity);
        } else {
            // Update existing
            AchievementMapper.updateEntity(entity, achievement);
            
            // Update verifiedBy if changed
            UUID existingVerifiedById = entity.getVerifiedBy() != null ? entity.getVerifiedBy().getId() : null;
            if (!Objects.equals(existingVerifiedById, achievement.getVerifiedBy())) {
                if (achievement.getVerifiedBy() != null) {
                    AdminEntity admin = getWriteEntityManager().find(AdminEntity.class, achievement.getVerifiedBy());
                    entity.setVerifiedBy(admin);
                } else {
                    entity.setVerifiedBy(null);
                }
            }
            
            entity = getWriteEntityManager().merge(entity);
        }
        
        return AchievementMapper.toDomain(entity);
    }

    @Override
    @Transactional
    public void delete(UUID id) {
        AchievementEntity entity = getWriteEntityManager().find(AchievementEntity.class, id);
        if (entity != null) {
            getWriteEntityManager().remove(entity);
        }
    }

    @Override
    @Transactional
    public int deleteRejectedOlderThan(Instant cutoff) {
        return getWriteEntityManager()
            .createQuery("DELETE FROM AchievementEntity a WHERE a.status = :status AND a.verifiedAt < :cutoff")
            .setParameter("status", AchievementStatus.REJECTED)
            .setParameter("cutoff", cutoff)
            .executeUpdate();
    }
}
