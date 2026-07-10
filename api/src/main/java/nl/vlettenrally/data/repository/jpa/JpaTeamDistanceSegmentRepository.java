package nl.vlettenrally.data.repository.jpa;

import io.quarkus.arc.profile.IfBuildProfile;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.transaction.Transactional;
import nl.vlettenrally.data.domain.TeamDistanceSegment;
import nl.vlettenrally.data.entity.jpa.TeamDistanceSegmentEntity;
import nl.vlettenrally.data.mapper.TeamDistanceSegmentMapper;
import nl.vlettenrally.data.repository.TeamDistanceSegmentRepository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * JPA implementation of TeamDistanceSegmentRepository.
 * Only active in prod profile - replaced by the mock in dev/test.
 * <p>
 * Always reads the primary datasource: a stale replica would miss the true anchor
 * (last segment) and double-count distance.
 * <p>
 * {@link #lockTeam} takes a transaction-scoped Postgres advisory lock keyed on the team id.
 * It replaces the old accumulator row's {@code PESSIMISTIC_WRITE} lock: the segment chain is
 * extended/rebuilt via read-then-insert from the Legendstracking poller, the Traccar/OsmAnd
 * endpoint, and backfill's recompute — potentially concurrently for the same team across
 * instances. The lock serializes those, is auto-released at commit/rollback, and (unlike a row
 * lock) also covers a team's very first fix, when there is no row to lock yet. The UNIQUE
 * constraints on (team_id, from_time) and (team_id, to_time) are the backstop: if
 * serialization is ever missed, a double-extend of the same anchor aborts loudly instead of
 * silently double-counting.
 */
@IfBuildProfile("prod")
@ApplicationScoped
public class JpaTeamDistanceSegmentRepository extends ReadReplicaAwareRepository
        implements TeamDistanceSegmentRepository {

    @Override
    public void lockTeam(UUID teamId) {
        // hashtextextended maps the UUID text to the bigint advisory-lock keyspace.
        getWriteEntityManager()
                .createNativeQuery("SELECT pg_advisory_xact_lock(hashtextextended(:teamId, 0))")
                .setParameter("teamId", teamId.toString())
                .getSingleResult();
    }

    @Override
    public Optional<TeamDistanceSegment> findLastByTeamId(UUID teamId) {
        return getReadEntityManager(true)
                .createQuery("SELECT s FROM TeamDistanceSegmentEntity s WHERE s.teamId = :teamId "
                        + "ORDER BY s.toTime DESC", TeamDistanceSegmentEntity.class)
                .setParameter("teamId", teamId)
                .setMaxResults(1)
                .getResultStream()
                .findFirst()
                .map(TeamDistanceSegmentMapper::toDomain);
    }

    @Override
    public double sumMetersByTeamId(UUID teamId) {
        Double result = getReadEntityManager(true)
                .createQuery("SELECT COALESCE(SUM(s.meters), 0) FROM TeamDistanceSegmentEntity s "
                        + "WHERE s.teamId = :teamId", Double.class)
                .setParameter("teamId", teamId)
                .getSingleResult();
        return result == null ? 0 : result;
    }

    @Transactional
    @Override
    public TeamDistanceSegment save(TeamDistanceSegment segment) {
        if (segment.getId() == null) {
            segment.setId(UUID.randomUUID());
        }
        TeamDistanceSegmentEntity entity = TeamDistanceSegmentMapper.toEntity(segment);
        getWriteEntityManager().persist(entity);
        return TeamDistanceSegmentMapper.toDomain(entity);
    }

    @Transactional
    @Override
    public void deleteFromTime(UUID teamId, Instant boundaryInclusive) {
        // Never delete a carryover seed (fromTime null, meters > 0): its distance may
        // predate the location_data retention window and cannot be re-derived.
        getWriteEntityManager()
                .createQuery("DELETE FROM TeamDistanceSegmentEntity s WHERE s.teamId = :teamId "
                        + "AND s.toTime >= :boundary AND (s.fromTime IS NOT NULL OR s.meters = 0)")
                .setParameter("teamId", teamId)
                .setParameter("boundary", boundaryInclusive)
                .executeUpdate();
    }

    @Override
    public List<TeamDistanceSegment> findByTeamId(UUID teamId) {
        return getReadEntityManager(true)
                .createQuery("SELECT s FROM TeamDistanceSegmentEntity s WHERE s.teamId = :teamId "
                        + "ORDER BY s.toTime ASC", TeamDistanceSegmentEntity.class)
                .setParameter("teamId", teamId)
                .getResultList()
                .stream()
                .map(TeamDistanceSegmentMapper::toDomain)
                .toList();
    }
}
