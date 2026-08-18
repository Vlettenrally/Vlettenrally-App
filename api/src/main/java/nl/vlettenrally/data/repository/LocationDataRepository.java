package nl.vlettenrally.data.repository;

import nl.vlettenrally.data.domain.LocationData;
import org.locationtech.jts.geom.Point;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Repository interface for LocationData entities.
 * Provides access to team location history with spatial query support.
 */
public interface LocationDataRepository {

    /**
     * Find a location by ID
     */
    Optional<LocationData> findById(UUID id);

    /**
     * Find all locations for a team, ordered by location_time descending
     */
    List<LocationData> findByTeamId(UUID teamId);

    /**
     * Find all locations for a team within a time range
     */
    List<LocationData> findByTeamIdAndTimeRange(UUID teamId, Instant startTime, Instant endTime);

    /**
     * Find a team's locations recorded strictly after the given time, ordered by
     * location_time ascending. Used to incrementally accumulate travelled distance
     * by reading only fixes newer than the last processed one.
     */
    List<LocationData> findByTeamIdAfter(UUID teamId, Instant afterExclusive);

    /**
     * Find the most recent location for a team
     */
    Optional<LocationData> findLatestByTeamId(UUID teamId);

    /**
     * The most recent location of every team in a game that has one, in a single query.
     * Teams without a stored fix are omitted, so this is the batch equivalent of calling
     * {@link #findLatestByTeamId} for each team of the game — which is what it replaces on
     * the admin map and the per-game sweeps, where one query per team meant hundreds of
     * round trips per request.
     */
    List<LocationData> findLatestByGameId(UUID gameId);

    /**
     * Find a team's most recent locations, ordered by location_time descending, capped at
     * {@code limit}. Used by the ingest outlier filter, which needs the last two fixes to
     * derive the prior travel bearing without replaying the whole track.
     */
    List<LocationData> findRecentByTeamId(UUID teamId, int limit);

    /**
     * Find all locations for a game, ordered by location_time descending
     */
    List<LocationData> findByGameId(UUID gameId);

    /**
     * Find all locations for a game within a time range
     */
    List<LocationData> findByGameIdAndTimeRange(UUID gameId, Instant startTime, Instant endTime);

    /**
     * Find locations within a specified distance (in meters) from a point.
     * Used for geofencing and proximity detection.
     *
     * @param point The center point (typically a checkpoint)
     * @param distanceMeters The search radius in meters
     * @return List of locations within the specified distance
     */
    List<LocationData> findWithinDistance(Point point, double distanceMeters);

    /**
     * Find locations of a specific team within a specified distance from a point.
     * Used for geofencing - detect if a team is near a checkpoint.
     *
     * @param teamId The team ID
     * @param point The center point (typically a checkpoint)
     * @param distanceMeters The search radius in meters (50m recommended)
     * @return List of team's locations within the specified distance
     */
    List<LocationData> findTeamLocationsWithinDistance(UUID teamId, Point point, double distanceMeters);

    /**
     * Find locations of a team within distance, within a time range.
     * Used for route tracking analysis.
     *
     * @param teamId The team ID
     * @param point The center point
     * @param distanceMeters The search radius in meters
     * @param startTime Start of time range
     * @param endTime End of time range
     * @return List of team's locations matching criteria
     */
    List<LocationData> findTeamLocationsWithinDistanceAndTime(UUID teamId, Point point,
                                                              double distanceMeters, Instant startTime, Instant endTime);

    /**
     * Check if two points are within a specified distance of each other.
     * Delegates to PostGIS ST_DWithin with geography cast for accurate meter-based distance.
     *
     * @param point1 The first point
     * @param point2 The second point
     * @param distanceMeters The maximum distance in meters
     * @return true if the points are within the specified distance
     */
    boolean isWithinDistance(Point point1, Point point2, double distanceMeters);

    /**
     * Save a location
     */
    LocationData save(LocationData location);

    /**
     * Delete a location by ID
     */
    void delete(UUID id);

    /**
     * Delete all locations for a team
     */
    void deleteByTeamId(UUID teamId);
}

