package nl.vlettenrally.data.repository.mock;

import io.quarkus.arc.profile.IfBuildProfile;
import jakarta.enterprise.context.ApplicationScoped;
import nl.vlettenrally.data.domain.TeamDistanceProgress;
import nl.vlettenrally.data.repository.TeamDistanceProgressRepository;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Mock implementation of TeamDistanceProgressRepository (starts empty).
 * Only active in dev and test profiles - replaced by the JPA impl in prod.
 */
@IfBuildProfile(anyOf = {"dev", "test"})
@ApplicationScoped
public class MockTeamDistanceProgressRepository implements TeamDistanceProgressRepository {

    private final Map<UUID, TeamDistanceProgress> progress = new ConcurrentHashMap<>();

    @Override
    public Optional<TeamDistanceProgress> findByTeamId(UUID teamId) {
        return Optional.ofNullable(progress.get(teamId));
    }

    @Override
    public TeamDistanceProgress save(TeamDistanceProgress p) {
        progress.put(p.getTeamId(), p);
        return p;
    }
}
