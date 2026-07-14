package nl.vlettenrally.data.repository.mock;

import io.quarkus.arc.profile.IfBuildProfile;
import jakarta.annotation.PostConstruct;
import jakarta.enterprise.context.ApplicationScoped;
import nl.vlettenrally.data.domain.Tracker;
import nl.vlettenrally.data.repository.TrackerRepository;
import nl.vlettenrally.data.repository.TrackerStatusUpdate;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * In-memory mock of TrackerRepository. Only active in dev and test profiles.
 */
@IfBuildProfile(anyOf = {"dev", "test"})
@ApplicationScoped
public class MockTrackerRepository implements TrackerRepository {

    private final Map<String, Tracker> trackers = new ConcurrentHashMap<>();

    @PostConstruct
    void init() {
        initializeData();
    }

    public void initializeData() {
        trackers.clear();

        Instant now = Instant.now();

        // A tracker recently seen with a healthy battery
        Tracker fresh = newTracker("100001");
        fresh.setBatteryPercentage(87);
        fresh.setLastSeenAt(now.minus(2, ChronoUnit.MINUTES));
        fresh.setLastLatitude(52.0907);
        fresh.setLastLongitude(5.1214);

        // A tracker not seen for a while with a low battery
        Tracker stale = newTracker("100002");
        stale.setBatteryPercentage(12);
        stale.setLastSeenAt(now.minus(3, ChronoUnit.HOURS));
        stale.setLastLatitude(52.0850);
        stale.setLastLongitude(5.1300);

        // A broken tracker taken out of rotation
        Tracker disabled = newTracker("100003");
        disabled.setEnabled(false);

        // A tracker that has never reported
        newTracker("100004");
    }

    private Tracker newTracker(String trackerId) {
        Tracker tracker = new Tracker();
        tracker.setId(UUID.randomUUID());
        tracker.setTrackerId(trackerId);
        tracker.setCreatedAt(Instant.now());
        trackers.put(trackerId, tracker);
        return tracker;
    }

    @Override
    public List<Tracker> findAll() {
        return trackers.values().stream()
                .sorted(Comparator.comparing(Tracker::getTrackerId))
                .map(this::copy)
                .toList();
    }

    @Override
    public Optional<Tracker> findByTrackerId(String trackerId) {
        return Optional.ofNullable(trackers.get(trackerId)).map(this::copy);
    }

    @Override
    public Set<String> findEnabledTrackerIds() {
        Set<String> ids = new TreeSet<>();
        for (Tracker tracker : trackers.values()) {
            if (tracker.isEnabled()) {
                ids.add(tracker.getTrackerId());
            }
        }
        return ids;
    }

    @Override
    public int addAll(Collection<String> trackerIds) {
        if (trackerIds == null || trackerIds.isEmpty()) {
            return 0;
        }
        int added = 0;
        for (String trackerId : trackerIds) {
            if (trackerId == null || trackerId.isBlank() || trackers.containsKey(trackerId)) {
                continue;
            }
            newTracker(trackerId);
            added++;
        }
        return added;
    }

    @Override
    public int deleteAll(Collection<String> trackerIds) {
        if (trackerIds == null || trackerIds.isEmpty()) {
            return 0;
        }
        int deleted = 0;
        for (String trackerId : trackerIds) {
            if (trackerId != null && trackers.remove(trackerId) != null) {
                deleted++;
            }
        }
        return deleted;
    }

    @Override
    public Optional<Tracker> setEnabled(String trackerId, boolean enabled) {
        Tracker tracker = trackers.get(trackerId);
        if (tracker == null) {
            return Optional.empty();
        }
        tracker.setEnabled(enabled);
        return Optional.of(copy(tracker));
    }

    @Override
    public void updateStatuses(Collection<TrackerStatusUpdate> updates) {
        if (updates == null) {
            return;
        }
        for (TrackerStatusUpdate update : updates) {
            // Update-only + newest-wins, mirroring the JPA implementation.
            Tracker tracker = trackers.get(update.trackerId());
            if (tracker == null || update.lastSeenAt() == null) {
                continue;
            }
            if (tracker.getLastSeenAt() != null && !tracker.getLastSeenAt().isBefore(update.lastSeenAt())) {
                continue;
            }
            tracker.setLastSeenAt(update.lastSeenAt());
            tracker.setBatteryPercentage(update.batteryPercentage());
            tracker.setLastLatitude(update.latitude());
            tracker.setLastLongitude(update.longitude());
        }
    }

    private Tracker copy(Tracker source) {
        Tracker tracker = new Tracker();
        tracker.setId(source.getId());
        tracker.setTrackerId(source.getTrackerId());
        tracker.setEnabled(source.isEnabled());
        tracker.setBatteryPercentage(source.getBatteryPercentage());
        tracker.setLastSeenAt(source.getLastSeenAt());
        tracker.setLastLatitude(source.getLastLatitude());
        tracker.setLastLongitude(source.getLastLongitude());
        tracker.setCreatedAt(source.getCreatedAt());
        return tracker;
    }
}
