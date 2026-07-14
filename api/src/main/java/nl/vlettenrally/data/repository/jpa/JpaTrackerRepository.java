package nl.vlettenrally.data.repository.jpa;

import io.quarkus.arc.profile.IfBuildProfile;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.transaction.Transactional;
import nl.vlettenrally.data.domain.Tracker;
import nl.vlettenrally.data.entity.jpa.TrackerEntity;
import nl.vlettenrally.data.mapper.TrackerMapper;
import nl.vlettenrally.data.repository.TrackerRepository;
import nl.vlettenrally.data.repository.TrackerStatusUpdate;

import java.util.Collection;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * JPA implementation of TrackerRepository. Only active in prod profile.
 */
@IfBuildProfile("prod")
@ApplicationScoped
public class JpaTrackerRepository extends ReadReplicaAwareRepository implements TrackerRepository {

    @Override
    public List<Tracker> findAll() {
        return getReadEntityManager()
                .createQuery("SELECT t FROM TrackerEntity t ORDER BY t.trackerId", TrackerEntity.class)
                .getResultList()
                .stream()
                .map(TrackerMapper::toDomain)
                .toList();
    }

    @Override
    public Optional<Tracker> findByTrackerId(String trackerId) {
        return getReadEntityManager()
                .createQuery("SELECT t FROM TrackerEntity t WHERE t.trackerId = :trackerId", TrackerEntity.class)
                .setParameter("trackerId", trackerId)
                .getResultStream()
                .findFirst()
                .map(TrackerMapper::toDomain);
    }

    @Override
    public Set<String> findEnabledTrackerIds() {
        return new LinkedHashSet<>(getReadEntityManager()
                .createQuery("SELECT t.trackerId FROM TrackerEntity t WHERE t.enabled = true ORDER BY t.trackerId", String.class)
                .getResultList());
    }

    @Override
    @Transactional
    public int addAll(Collection<String> trackerIds) {
        if (trackerIds == null || trackerIds.isEmpty()) {
            return 0;
        }
        Set<String> seen = new HashSet<>(getWriteEntityManager()
                .createQuery("SELECT t.trackerId FROM TrackerEntity t", String.class)
                .getResultList());
        int added = 0;
        for (String trackerId : trackerIds) {
            if (trackerId == null || trackerId.isBlank() || !seen.add(trackerId)) {
                continue;
            }
            TrackerEntity entity = new TrackerEntity();
            entity.setTrackerId(trackerId);
            getWriteEntityManager().persist(entity);
            added++;
        }
        return added;
    }

    @Override
    @Transactional
    public int deleteAll(Collection<String> trackerIds) {
        if (trackerIds == null || trackerIds.isEmpty()) {
            return 0;
        }
        return getWriteEntityManager()
                .createQuery("DELETE FROM TrackerEntity t WHERE t.trackerId IN :trackerIds")
                .setParameter("trackerIds", trackerIds)
                .executeUpdate();
    }

    @Override
    @Transactional
    public Optional<Tracker> setEnabled(String trackerId, boolean enabled) {
        Optional<TrackerEntity> entity = getWriteEntityManager()
                .createQuery("SELECT t FROM TrackerEntity t WHERE t.trackerId = :trackerId", TrackerEntity.class)
                .setParameter("trackerId", trackerId)
                .getResultStream()
                .findFirst();
        entity.ifPresent(e -> e.setEnabled(enabled));
        return entity.map(TrackerMapper::toDomain);
    }

    @Override
    @Transactional
    public void updateStatuses(Collection<TrackerStatusUpdate> updates) {
        if (updates == null || updates.isEmpty()) {
            return;
        }
        // Update-only (unknown feed trackers must not self-register); the
        // last_seen_at guard makes stale or replayed feed entries a no-op.
        for (TrackerStatusUpdate update : updates) {
            getWriteEntityManager()
                    .createQuery("UPDATE TrackerEntity t SET t.batteryPercentage = :battery, "
                            + "t.lastSeenAt = :lastSeen, t.lastLatitude = :lat, t.lastLongitude = :lng "
                            + "WHERE t.trackerId = :trackerId "
                            + "AND (t.lastSeenAt IS NULL OR t.lastSeenAt < :lastSeen)")
                    .setParameter("battery", update.batteryPercentage())
                    .setParameter("lastSeen", update.lastSeenAt())
                    .setParameter("lat", update.latitude())
                    .setParameter("lng", update.longitude())
                    .setParameter("trackerId", update.trackerId())
                    .executeUpdate();
        }
    }
}
