package nl.vlettenrally.data.repository;

import java.util.Collection;
import java.util.Set;
import java.util.UUID;

/**
 * Stores the pool of physical tracker IDs known for a game. Assignment to teams
 * lives on {@code teams.tracker_id}; this only tracks which IDs exist, so that
 * trackers not auto-assigned stay pickable per team.
 */
public interface TrackerPoolRepository {

    /** All tracker IDs in the game's pool. */
    Set<String> findTrackerIdsByGameId(UUID gameId);

    /** Add tracker IDs to the pool, ignoring any that are already present (idempotent). */
    void addAll(UUID gameId, Collection<String> trackerIds);

    /** Remove a tracker ID from the pool. No-op if absent. */
    void remove(UUID gameId, String trackerId);
}
