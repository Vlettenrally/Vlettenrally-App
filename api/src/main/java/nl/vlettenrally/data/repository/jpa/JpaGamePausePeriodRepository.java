package nl.vlettenrally.data.repository.jpa;

import io.quarkus.arc.profile.IfBuildProfile;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.transaction.Transactional;
import nl.vlettenrally.data.domain.GamePausePeriod;
import nl.vlettenrally.data.entity.jpa.GamePausePeriodEntity;
import nl.vlettenrally.data.mapper.GamePausePeriodMapper;
import nl.vlettenrally.data.repository.GamePausePeriodRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * JPA implementation of GamePausePeriodRepository.
 * Only active in prod profile - replaced by the mock in dev/test.
 * <p>
 * Always reads the primary datasource: the ingest path decides whether to credit
 * distance from these windows, and replication lag on a just-closed pause would
 * silently discard real movement.
 */
@IfBuildProfile("prod")
@ApplicationScoped
public class JpaGamePausePeriodRepository extends ReadReplicaAwareRepository
        implements GamePausePeriodRepository {

    @Override
    public List<GamePausePeriod> findByGameId(UUID gameId) {
        return getReadEntityManager(true)
                .createQuery("SELECT p FROM GamePausePeriodEntity p WHERE p.gameId = :gameId "
                        + "ORDER BY p.pausedAt ASC", GamePausePeriodEntity.class)
                .setParameter("gameId", gameId)
                .getResultList()
                .stream()
                .map(GamePausePeriodMapper::toDomain)
                .toList();
    }

    @Override
    public Optional<GamePausePeriod> findOpenByGameId(UUID gameId) {
        return getReadEntityManager(true)
                .createQuery("SELECT p FROM GamePausePeriodEntity p WHERE p.gameId = :gameId "
                        + "AND p.resumedAt IS NULL", GamePausePeriodEntity.class)
                .setParameter("gameId", gameId)
                .getResultStream()
                .findFirst()
                .map(GamePausePeriodMapper::toDomain);
    }

    @Transactional
    @Override
    public GamePausePeriod save(GamePausePeriod period) {
        if (period.getId() == null) {
            period.setId(UUID.randomUUID());
        }
        GamePausePeriodEntity existing = getWriteEntityManager()
                .find(GamePausePeriodEntity.class, period.getId());
        if (existing == null) {
            GamePausePeriodEntity entity = GamePausePeriodMapper.toEntity(period);
            getWriteEntityManager().persist(entity);
            return GamePausePeriodMapper.toDomain(entity);
        }
        GamePausePeriodMapper.applyResume(existing, period);
        return GamePausePeriodMapper.toDomain(getWriteEntityManager().merge(existing));
    }
}
