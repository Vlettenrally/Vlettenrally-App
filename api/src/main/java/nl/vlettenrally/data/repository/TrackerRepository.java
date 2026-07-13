package nl.vlettenrally.data.repository;

import nl.vlettenrally.data.domain.Tracker;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * Global pool of physical GPS tracker IDs. Membership and health status only;
 * assignment to teams lives on {@code teams.tracker_id}.
 */
public interface TrackerRepository {

    /** All trackers in the pool, ordered by tracker ID. */
    List<Tracker> findAll();

    Optional<Tracker> findByTrackerId(String trackerId);

    /** Tracker IDs eligible for assignment (disabled ones are excluded). */
    Set<String> findEnabledTrackerIds();

    /**
     * Add tracker IDs to the pool, ignoring any already present (idempotent).
     *
     * @return the number of trackers newly added
     */
    int addAll(Collection<String> trackerIds);

    /** @return the updated tracker, or empty if the ID is not in the pool */
    Optional<Tracker> setEnabled(String trackerId, boolean enabled);

    /**
     * Batch-update tracker status from a polled location feed. Update-only:
     * IDs not in the pool are ignored (unknown feed trackers must not
     * self-register), and an update older than the stored {@code lastSeenAt}
     * is a no-op, so out-of-order or replayed feeds cannot regress status.
     */
    void updateStatuses(Collection<TrackerStatusUpdate> updates);
}
