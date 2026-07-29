package nl.vlettenrally.data.repository.jpa;

import io.quarkus.arc.profile.IfBuildProfile;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.persistence.TypedQuery;
import jakarta.transaction.Transactional;
import nl.vlettenrally.data.domain.Picture;
import nl.vlettenrally.data.entity.jpa.AdminEntity;
import nl.vlettenrally.data.entity.jpa.GameEntity;
import nl.vlettenrally.data.entity.jpa.PictureEntity;
import nl.vlettenrally.data.entity.jpa.TeamEntity;
import nl.vlettenrally.data.enums.PictureStatus;
import nl.vlettenrally.data.mapper.PictureMapper;
import nl.vlettenrally.data.repository.PictureRepository;

import java.time.Instant;
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
    public List<Picture> findReadyForModerationPage(UUID gameId, Instant cursorAt, UUID cursorId, int limit) {
        StringBuilder jpql = new StringBuilder(
                "SELECT p FROM PictureEntity p WHERE p.status = :status AND p.uploadedAt IS NOT NULL");
        if (gameId != null) {
            jpql.append(" AND p.game.id = :gameId");
        }
        // Oldest first: the queue is FIFO, so the longest-waiting team is moderated first.
        appendCursor(jpql, "p.uploadedAt", cursorAt, true);
        jpql.append(" ORDER BY p.uploadedAt ASC, p.id ASC");

        TypedQuery<PictureEntity> query = getReadEntityManager()
                .createQuery(jpql.toString(), PictureEntity.class)
                .setParameter("status", PictureStatus.PENDING);
        if (gameId != null) {
            query.setParameter("gameId", gameId);
        }
        return page(query, cursorAt, cursorId, limit);
    }

    @Override
    public List<Picture> findApprovedPage(UUID gameId, Instant cursorAt, UUID cursorId, int limit) {
        StringBuilder jpql = new StringBuilder(
                "SELECT p FROM PictureEntity p WHERE p.status = :status");
        if (gameId != null) {
            jpql.append(" AND p.game.id = :gameId");
        }
        appendCursor(jpql, "p.uploadedAt", cursorAt, false);
        jpql.append(" ORDER BY p.uploadedAt DESC, p.id DESC");

        TypedQuery<PictureEntity> query = getReadEntityManager()
                .createQuery(jpql.toString(), PictureEntity.class)
                .setParameter("status", PictureStatus.APPROVED);
        if (gameId != null) {
            query.setParameter("gameId", gameId);
        }
        return page(query, cursorAt, cursorId, limit);
    }

    @Override
    public List<Picture> findPage(PictureStatus status, UUID gameId, Instant cursorAt, UUID cursorId, int limit) {
        StringBuilder jpql = new StringBuilder("SELECT p FROM PictureEntity p WHERE 1 = 1");
        if (status != null) {
            jpql.append(" AND p.status = :status");
        }
        if (gameId != null) {
            jpql.append(" AND p.game.id = :gameId");
        }
        appendCursor(jpql, "p.createdAt", cursorAt, false);
        jpql.append(" ORDER BY p.createdAt DESC, p.id DESC");

        TypedQuery<PictureEntity> query = getReadEntityManager()
                .createQuery(jpql.toString(), PictureEntity.class);
        if (status != null) {
            query.setParameter("status", status);
        }
        if (gameId != null) {
            query.setParameter("gameId", gameId);
        }
        return page(query, cursorAt, cursorId, limit);
    }

    /**
     * Append the keyset predicate for a cursor page. {@code ascending} selects the comparison
     * direction; it must match the ORDER BY the caller appends, or pages will overlap.
     * <p>
     * Postgres applies this OR-form as a filter on top of the index scan rather than as an index
     * bound, so a deep page still walks (and discards) the rows before its cursor. Measured
     * against 20k pictures that is ~1.7ms at the worst depth — far below the ~{@code pageSize}
     * S3 presigns each response already pays for. The row-value form
     * {@code (uploaded_at, id) < (:cursorAt, :cursorId)} would become a true index bound, but
     * JPQL support for row-value constructors is dialect-dependent and nothing in the test suite
     * exercises JPQL against a real database, so it is not worth the risk here.
     */
    private void appendCursor(StringBuilder jpql, String sortColumn, Instant cursorAt, boolean ascending) {
        if (cursorAt == null) {
            return;
        }
        String op = ascending ? ">" : "<";
        jpql.append(" AND (").append(sortColumn).append(' ').append(op).append(" :cursorAt")
            .append(" OR (").append(sortColumn).append(" = :cursorAt AND p.id ").append(op).append(" :cursorId))");
    }

    private List<Picture> page(TypedQuery<PictureEntity> query, Instant cursorAt, UUID cursorId, int limit) {
        if (cursorAt != null) {
            query.setParameter("cursorAt", cursorAt).setParameter("cursorId", cursorId);
        }
        return query.setMaxResults(limit)
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
    public long countModeratedBy(UUID adminId) {
        // Primary EM: this count gates an irreversible admin delete, so replication lag
        // must not let a moderation record slip through unseen.
        return getReadEntityManager(true)
                .createQuery("SELECT COUNT(p) FROM PictureEntity p WHERE p.moderatedBy.id = :adminId", Long.class)
                .setParameter("adminId", adminId)
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
