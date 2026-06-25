package nl.vlettenrally.data.repository.mock;

import io.quarkus.arc.profile.IfBuildProfile;
import jakarta.enterprise.context.ApplicationScoped;
import nl.vlettenrally.data.repository.TrackerPoolRepository;

import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * In-memory mock of TrackerPoolRepository. Only active in dev and test profiles.
 */
@IfBuildProfile(anyOf = {"dev", "test"})
@ApplicationScoped
public class MockTrackerPoolRepository implements TrackerPoolRepository {

    private final Map<UUID, Set<String>> pools = new ConcurrentHashMap<>();

    @Override
    public Set<String> findTrackerIdsByGameId(UUID gameId) {
        return new LinkedHashSet<>(pools.getOrDefault(gameId, Set.of()));
    }

    @Override
    public void addAll(UUID gameId, Collection<String> trackerIds) {
        if (trackerIds == null || trackerIds.isEmpty()) {
            return;
        }
        Set<String> pool = pools.computeIfAbsent(gameId, g -> ConcurrentHashMap.newKeySet());
        for (String trackerId : trackerIds) {
            if (trackerId != null && !trackerId.isBlank()) {
                pool.add(trackerId);
            }
        }
    }

    @Override
    public void remove(UUID gameId, String trackerId) {
        Set<String> pool = pools.get(gameId);
        if (pool != null) {
            pool.remove(trackerId);
        }
    }
}
