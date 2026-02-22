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
     * Find the most recent location for a team
     */
    Optional<LocationData> findLatestByTeamId(UUID teamId);

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

