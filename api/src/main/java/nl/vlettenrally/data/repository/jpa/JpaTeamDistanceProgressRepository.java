package nl.vlettenrally.data.repository.jpa;

import io.quarkus.arc.profile.IfBuildProfile;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.persistence.LockModeType;
import jakarta.transaction.Transactional;
import nl.vlettenrally.data.domain.TeamDistanceProgress;
import nl.vlettenrally.data.entity.jpa.TeamDistanceProgressEntity;
import nl.vlettenrally.data.mapper.TeamDistanceProgressMapper;
import nl.vlettenrally.data.repository.TeamDistanceProgressRepository;

import java.util.Optional;
import java.util.UUID;

/**
 * JPA implementation of TeamDistanceProgressRepository.
 * Only active in prod profile - replaced by the mock in dev/test.
 * <p>
 * Always reads the primary datasource: a stale accumulator would re-walk fixes
 * and double-credit distance points.
 * <p>
 * {@link #findByTeamId} takes a {@code PESSIMISTIC_WRITE} lock on the row: its only callers
 * ({@link nl.vlettenrally.service.TeamDistanceCalculator#computeTraveledMeters} and
 * {@code #recomputeFromScratch}) always follow it with a read-modify-{@link #save}, and are
 * reachable concurrently for the same team from the Legendstracking poller, the Traccar/OsmAnd
 * endpoint, and auto-backfill's full-track recompute. Without a lock here, two overlapping
 * transactions can race this read-modify-write and regress the persisted cursor, causing
 * already-counted track to be re-walked and re-credited on every subsequent tick. The lock is
 * held for the life of the enclosing {@code @Transactional} call, so it also serializes that
 * team's {@code automation_distance_progress} update, which always happens afterwards in the
 * same transaction.
 */
@IfBuildProfile("prod")
@ApplicationScoped
public class JpaTeamDistanceProgressRepository extends ReadReplicaAwareRepository
        implements TeamDistanceProgressRepository {

    @Override
    public Optional<TeamDistanceProgress> findByTeamId(UUID teamId) {
        return Optional.ofNullable(
                getReadEntityManager(true).find(TeamDistanceProgressEntity.class, teamId, LockModeType.PESSIMISTIC_WRITE))
                .map(TeamDistanceProgressMapper::toDomain);
    }

    @Transactional
    @Override
    public TeamDistanceProgress save(TeamDistanceProgress progress) {
        TeamDistanceProgressEntity existing =
                getWriteEntityManager().find(TeamDistanceProgressEntity.class, progress.getTeamId());
        if (existing != null) {
            TeamDistanceProgressMapper.updateEntity(existing, progress);
            return TeamDistanceProgressMapper.toDomain(existing);
        }

        TeamDistanceProgressEntity entity = TeamDistanceProgressMapper.toEntity(progress);
        getWriteEntityManager().persist(entity);
        return TeamDistanceProgressMapper.toDomain(entity);
    }
}
