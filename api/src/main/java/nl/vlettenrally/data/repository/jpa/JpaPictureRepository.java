package nl.vlettenrally.data.repository.jpa;

import io.quarkus.arc.profile.IfBuildProfile;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.transaction.Transactional;
import nl.vlettenrally.data.domain.Picture;
import nl.vlettenrally.data.entity.jpa.AdminEntity;
import nl.vlettenrally.data.entity.jpa.GameEntity;
import nl.vlettenrally.data.entity.jpa.PictureEntity;
import nl.vlettenrally.data.entity.jpa.TeamEntity;
import nl.vlettenrally.data.enums.PictureStatus;
import nl.vlettenrally.data.mapper.PictureMapper;
import nl.vlettenrally.data.repository.PictureRepository;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * JPA implementation of PictureRepository with read replica support.
 * Only active in prod profile - replaced by MockPictureRepository in dev/test.
 */
@IfBuildProfile("prod")
@ApplicationScoped
public class JpaPictureRepository extends ReadReplicaAwareRepository implements PictureRepository {

    @Override
    public Optional<Picture> findById(UUID id) {
        PictureEntity entity = getReadEntityManager().find(PictureEntity.class, id);
        return Optional.ofNullable(PictureMapper.toDomain(entity));
    }

    @Override
    public List<Picture> findAll() {
        return getReadEntityManager()
                .createQuery("SELECT p FROM PictureEntity p ORDER BY p.createdAt DESC", PictureEntity.class)
                .getResultList().stream()
                .map(PictureMapper::toDomain)
                .collect(Collectors.toList());
    }

    @Override
    public List<Picture> findByTeamId(UUID teamId) {
        return getReadEntityManager()
                .createQuery("SELECT p FROM PictureEntity p WHERE p.team.id = :teamId ORDER BY p.createdAt DESC", PictureEntity.class)
                .setParameter("teamId", teamId)
                .getResultList().stream()
                .map(PictureMapper::toDomain)
                .collect(Collectors.toList());
    }

    @Override
    public List<Picture> findByStatus(PictureStatus status) {
        return getReadEntityManager()
                .createQuery("SELECT p FROM PictureEntity p WHERE p.status = :status ORDER BY p.createdAt DESC", PictureEntity.class)
                .setParameter("status", status)
                .getResultList().stream()
                .map(PictureMapper::toDomain)
                .collect(Collectors.toList());
    }

    @Override
    public List<Picture> findReadyForModeration() {
        return getReadEntityManager()
                .createQuery("SELECT p FROM PictureEntity p WHERE p.status = :status AND p.uploadedAt IS NOT NULL ORDER BY p.uploadedAt ASC", PictureEntity.class)
                .setParameter("status", PictureStatus.PENDING)
                .getResultList().stream()
                .map(PictureMapper::toDomain)
                .collect(Collectors.toList());
    }

    @Override
    public List<Picture> findReadyForModerationByGameId(UUID gameId) {
        return getReadEntityManager()
                .createQuery("SELECT p FROM PictureEntity p WHERE p.game.id = :gameId AND p.status = :status AND p.uploadedAt IS NOT NULL ORDER BY p.uploadedAt ASC", PictureEntity.class)
                .setParameter("gameId", gameId)
                .setParameter("status", PictureStatus.PENDING)
                .getResultList().stream()
                .map(PictureMapper::toDomain)
                .collect(Collectors.toList());
    }

    @Override
    public List<Picture> findByStatusAndGameId(PictureStatus status, UUID gameId) {
        return getReadEntityManager()
                .createQuery("SELECT p FROM PictureEntity p WHERE p.game.id = :gameId AND p.status = :status ORDER BY p.createdAt DESC", PictureEntity.class)
                .setParameter("gameId", gameId)
                .setParameter("status", status)
                .getResultList().stream()
                .map(PictureMapper::toDomain)
                .collect(Collectors.toList());
    }

    @Override
    public List<Picture> findAllByGameId(UUID gameId) {
        return getReadEntityManager()
                .createQuery("SELECT p FROM PictureEntity p WHERE p.game.id = :gameId ORDER BY p.createdAt DESC", PictureEntity.class)
                .setParameter("gameId", gameId)
                .getResultList().stream()
                .map(PictureMapper::toDomain)
                .collect(Collectors.toList());
    }

    @Override
    public List<Picture> findApprovedByGameId(UUID gameId) {
        return getReadEntityManager()
                .createQuery("SELECT p FROM PictureEntity p WHERE p.game.id = :gameId AND p.status = :status ORDER BY p.uploadedAt DESC", PictureEntity.class)
                .setParameter("gameId", gameId)
                .setParameter("status", PictureStatus.APPROVED)
                .getResultList().stream()
                .map(PictureMapper::toDomain)
                .collect(Collectors.toList());
    }

    @Override
    public long countActiveByTeamId(UUID teamId) {
        // Count toward the per-team cap: everything except REJECTED. Uses the primary
        // EM so the count is immediately consistent for the slot-reservation check.
        return getReadEntityManager(true)
                .createQuery("SELECT COUNT(p) FROM PictureEntity p WHERE p.team.id = :teamId AND p.status <> :rejected", Long.class)
                .setParameter("teamId", teamId)
                .setParameter("rejected", PictureStatus.REJECTED)
                .getSingleResult();
    }

    @Override
    public long countAllByTeamId(UUID teamId) {
        return getReadEntityManager(true)
                .createQuery("SELECT COUNT(p) FROM PictureEntity p WHERE p.team.id = :teamId", Long.class)
                .setParameter("teamId", teamId)
                .getSingleResult();
    }

    @Override
    @Transactional
    public Picture save(Picture picture) {
        PictureEntity entity = getWriteEntityManager().find(PictureEntity.class, picture.getId());

        if (entity == null) {
            entity = PictureMapper.toEntity(picture);

            if (picture.getTeamId() != null) {
                entity.setTeam(getWriteEntityManager().find(TeamEntity.class, picture.getTeamId()));
            }
            if (picture.getGameId() != null) {
                entity.setGame(getWriteEntityManager().find(GameEntity.class, picture.getGameId()));
            }
            if (picture.getModeratedBy() != null) {
                entity.setModeratedBy(getWriteEntityManager().find(AdminEntity.class, picture.getModeratedBy()));
            }

            getWriteEntityManager().persist(entity);
        } else {
            PictureMapper.updateEntity(entity, picture);

            UUID existingModeratorId = entity.getModeratedBy() != null ? entity.getModeratedBy().getId() : null;
            if (!Objects.equals(existingModeratorId, picture.getModeratedBy())) {
                entity.setModeratedBy(picture.getModeratedBy() != null
                        ? getWriteEntityManager().find(AdminEntity.class, picture.getModeratedBy())
                        : null);
            }

            entity = getWriteEntityManager().merge(entity);
        }

        return PictureMapper.toDomain(entity);
    }

    @Override
    @Transactional
    public void delete(UUID id) {
        PictureEntity entity = getWriteEntityManager().find(PictureEntity.class, id);
        if (entity != null) {
            getWriteEntityManager().remove(entity);
        }
    }
}
