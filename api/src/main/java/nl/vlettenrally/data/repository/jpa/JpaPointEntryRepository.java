package nl.vlettenrally.data.repository.jpa;

import io.quarkus.arc.profile.IfBuildProfile;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.persistence.TypedQuery;
import jakarta.transaction.Transactional;
import nl.vlettenrally.data.domain.PointEntry;
import nl.vlettenrally.data.entity.jpa.GameEntity;
import nl.vlettenrally.data.entity.jpa.PointEntryEntity;
import nl.vlettenrally.data.entity.jpa.TeamEntity;
import nl.vlettenrally.data.enums.PointEntryType;
import nl.vlettenrally.data.mapper.PointEntryMapper;
import nl.vlettenrally.data.repository.PointEntryRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * JPA implementation of PointEntryRepository.
 * Only active in prod profile - replaced by MockPointEntryRepository in dev/test.
 */
@IfBuildProfile("prod")
@ApplicationScoped
public class JpaPointEntryRepository extends ReadReplicaAwareRepository implements PointEntryRepository {

    @Override
    public Optional<PointEntry> findById(UUID id) {
        return Optional.ofNullable(getReadEntityManager().find(PointEntryEntity.class, id))
                .map(PointEntryMapper::toDomain);
    }

    @Override
    public List<PointEntry> findByGame(UUID gameId, UUID teamId, PointEntryType entryType,
                                      int page, int pageSize) {
        StringBuilder jpql = new StringBuilder("SELECT e FROM PointEntryEntity e WHERE e.game.id = :gameId");
        if (teamId != null)    jpql.append(" AND e.team.id = :teamId");
        if (entryType != null) jpql.append(" AND e.entryType = :entryType");
        jpql.append(" ORDER BY e.updatedAt DESC");

        TypedQuery<PointEntryEntity> query = getReadEntityManager()
                .createQuery(jpql.toString(), PointEntryEntity.class)
                .setParameter("gameId", gameId)
                .setFirstResult(page * pageSize)
                .setMaxResults(pageSize);
        if (teamId != null)    query.setParameter("teamId", teamId);
        if (entryType != null) query.setParameter("entryType", entryType);

        return query.getResultList().stream()
                .map(PointEntryMapper::toDomain)
                .collect(Collectors.toList());
    }

    @Override
    public long countByGame(UUID gameId, UUID teamId, PointEntryType entryType) {
        StringBuilder jpql = new StringBuilder("SELECT COUNT(e) FROM PointEntryEntity e WHERE e.game.id = :gameId");
        if (teamId != null)    jpql.append(" AND e.team.id = :teamId");
        if (entryType != null) jpql.append(" AND e.entryType = :entryType");

        TypedQuery<Long> query = getReadEntityManager()
                .createQuery(jpql.toString(), Long.class)
                .setParameter("gameId", gameId);
        if (teamId != null)    query.setParameter("teamId", teamId);
        if (entryType != null) query.setParameter("entryType", entryType);

        return query.getSingleResult();
    }

    @Override
    public int sumCurrentAmountByTeam(UUID teamId) {
        Long result = getReadEntityManager()
                .createQuery("SELECT COALESCE(SUM(e.currentAmount), 0) FROM PointEntryEntity e WHERE e.team.id = :teamId", Long.class)
                .setParameter("teamId", teamId)
                .getSingleResult();
        return result == null ? 0 : result.intValue();
    }

    @Override
    public int sumCurrentAmountByGame(UUID gameId, PointEntryType entryType) {
        StringBuilder jpql = new StringBuilder(
                "SELECT COALESCE(SUM(e.currentAmount), 0) FROM PointEntryEntity e WHERE e.game.id = :gameId");
        if (entryType != null) jpql.append(" AND e.entryType = :entryType");

        TypedQuery<Long> query = getReadEntityManager()
                .createQuery(jpql.toString(), Long.class)
                .setParameter("gameId", gameId);
        if (entryType != null) query.setParameter("entryType", entryType);

        Long result = query.getSingleResult();
        return result == null ? 0 : result.intValue();
    }

    @Override
    public long countByCreatedByOrEditedBy(UUID adminId) {
        // Primary EM: this count gates an irreversible admin delete, so replication lag
        // must not let a ledger entry slip through unseen.
        return getReadEntityManager(true)
                .createQuery("SELECT COUNT(e) FROM PointEntryEntity e "
                        + "WHERE e.createdBy = :adminId OR e.editedBy = :adminId", Long.class)
                .setParameter("adminId", adminId)
                .getSingleResult();
    }

    @Transactional
    @Override
    public PointEntry save(PointEntry entry) {
        PointEntryEntity existing = entry.getId() != null
                ? getWriteEntityManager().find(PointEntryEntity.class, entry.getId())
                : null;

        if (existing != null) {
            PointEntryMapper.updateEntity(existing, entry);
            return PointEntryMapper.toDomain(existing);
        }

        PointEntryEntity entity = PointEntryMapper.toEntity(entry);
        if (entry.getTeamId() != null) {
            entity.setTeam(getWriteEntityManager().getReference(TeamEntity.class, entry.getTeamId()));
        }
        if (entry.getGameId() != null) {
            entity.setGame(getWriteEntityManager().getReference(GameEntity.class, entry.getGameId()));
        }
        getWriteEntityManager().persist(entity);
        return PointEntryMapper.toDomain(entity);
    }

    @Transactional
    @Override
    public void delete(UUID id) {
        getWriteEntityManager().createQuery("DELETE FROM PointEntryEntity e WHERE e.id = :id")
                .setParameter("id", id)
                .executeUpdate();
    }

    @Transactional
    @Override
    public void deleteByTeamAndGame(UUID teamId, UUID gameId) {
        getWriteEntityManager()
                .createQuery("DELETE FROM PointEntryEntity e WHERE e.team.id = :teamId AND e.game.id = :gameId")
                .setParameter("teamId", teamId)
                .setParameter("gameId", gameId)
                .executeUpdate();
    }
}
