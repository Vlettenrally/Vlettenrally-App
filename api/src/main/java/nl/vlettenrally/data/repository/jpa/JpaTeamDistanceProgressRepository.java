package nl.vlettenrally.data.repository.jpa;

import io.quarkus.arc.profile.IfBuildProfile;
import jakarta.enterprise.context.ApplicationScoped;
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
 */
@IfBuildProfile("prod")
@ApplicationScoped
public class JpaTeamDistanceProgressRepository extends ReadReplicaAwareRepository
        implements TeamDistanceProgressRepository {

    @Override
    public Optional<TeamDistanceProgress> findByTeamId(UUID teamId) {
        return Optional.ofNullable(
                getReadEntityManager(true).find(TeamDistanceProgressEntity.class, teamId))
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
