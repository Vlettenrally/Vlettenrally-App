package nl.vlettenrally.data.repository.jpa;

import io.quarkus.arc.profile.IfBuildProfile;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.transaction.Transactional;
import nl.vlettenrally.data.entity.jpa.GameTrackerEntity;
import nl.vlettenrally.data.repository.TrackerPoolRepository;

import java.util.Collection;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.UUID;

/**
 * JPA implementation of TrackerPoolRepository. Only active in prod profile.
 */
@IfBuildProfile("prod")
@ApplicationScoped
public class JpaTrackerPoolRepository extends ReadReplicaAwareRepository implements TrackerPoolRepository {

    @Override
    public Set<String> findTrackerIdsByGameId(UUID gameId) {
        return new LinkedHashSet<>(getReadEntityManager()
                .createQuery("SELECT t.trackerId FROM GameTrackerEntity t WHERE t.gameId = :gameId ORDER BY t.trackerId", String.class)
                .setParameter("gameId", gameId)
                .getResultList());
    }

    @Override
    @Transactional
    public void addAll(UUID gameId, Collection<String> trackerIds) {
        if (trackerIds == null || trackerIds.isEmpty()) {
            return;
        }
        Set<String> existing = findTrackerIdsByGameId(gameId);
        Set<String> seen = new HashSet<>(existing);
        for (String trackerId : trackerIds) {
            if (trackerId == null || trackerId.isBlank() || !seen.add(trackerId)) {
                continue;
            }
            GameTrackerEntity entity = new GameTrackerEntity();
            entity.setGameId(gameId);
            entity.setTrackerId(trackerId);
            getWriteEntityManager().persist(entity);
        }
    }

    @Override
    @Transactional
    public void remove(UUID gameId, String trackerId) {
        getWriteEntityManager()
                .createQuery("DELETE FROM GameTrackerEntity t WHERE t.gameId = :gameId AND t.trackerId = :trackerId")
                .setParameter("gameId", gameId)
                .setParameter("trackerId", trackerId)
                .executeUpdate();
    }
}
