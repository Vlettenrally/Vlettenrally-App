package nl.vlettenrally.service;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import nl.vlettenrally.data.domain.Checkpoint;
import nl.vlettenrally.data.domain.LocationData;
import nl.vlettenrally.data.repository.LocationDataRepository;
import org.locationtech.jts.geom.Point;

import java.util.List;
import java.util.UUID;

/**
 * Service for geofencing operations.
 * Detects when teams approach checkpoints and triggers notifications.
 * Supports both circle (radius-based) and rectangle (bounding-box) zone types.
 */
@ApplicationScoped
public class GeofencingService {

    private static final double DEFAULT_GEOFENCE_RADIUS_METERS = 50.0;

    @Inject
    LocationDataRepository locationDataRepository;

    /**
     * Check if a location is within the geofence boundary of a checkpoint.
     * Supports both circle (radius) and rectangle (bounding box) zone types.
     *
     * @param teamLocation The team's current location
     * @param checkpoint The checkpoint to check proximity to
     * @return true if team is within the checkpoint's zone
     */
    public boolean isWithinCheckpointGeofence(LocationData teamLocation, Checkpoint checkpoint) {
        if (teamLocation == null || teamLocation.getLocation() == null || checkpoint == null) {
            return false;
        }

        if ("rectangle".equals(checkpoint.getZoneType())) {
            return isWithinRectangle(teamLocation, checkpoint);
        }

        // Default: circle geofence
        if (checkpoint.getLocation() == null) {
            return false;
        }
        double radius = checkpoint.getRadiusMeters() > 0
            ? checkpoint.getRadiusMeters()
            : DEFAULT_GEOFENCE_RADIUS_METERS;
        return locationDataRepository.isWithinDistance(
            teamLocation.getLocation(), checkpoint.getLocation(), radius);
    }

    /**
     * Check if a team location falls within a rectangular bounding box.
     */
    private boolean isWithinRectangle(LocationData teamLocation, Checkpoint checkpoint) {
        if (checkpoint.getBoundsNorth() == null || checkpoint.getBoundsSouth() == null ||
            checkpoint.getBoundsEast() == null || checkpoint.getBoundsWest() == null) {
            return false;
        }
        double lat = teamLocation.getLocation().getY();
        double lng = teamLocation.getLocation().getX();
        return lat <= checkpoint.getBoundsNorth() && lat >= checkpoint.getBoundsSouth()
            && lng <= checkpoint.getBoundsEast() && lng >= checkpoint.getBoundsWest();
    }

    /**
     * Find all recent locations of a team within checkpoint's geofence.
     * Uses the checkpoint's configured radius.
     *
     * @param teamId The team ID
     * @param checkpointLocation The checkpoint location (PostGIS Point)
     * @param radiusMeters The search radius in meters
     * @return List of team's locations within the specified radius of checkpoint
     */
    public List<LocationData> findTeamLocationsNearCheckpoint(UUID teamId, Point checkpointLocation, double radiusMeters) {
        double radius = radiusMeters > 0 ? radiusMeters : DEFAULT_GEOFENCE_RADIUS_METERS;
        return locationDataRepository.findTeamLocationsWithinDistance(
            teamId, checkpointLocation, radius);
    }

    /**
     * Find all recent locations of a team within checkpoint's geofence using default radius.
     *
     * @param teamId The team ID
     * @param checkpointLocation The checkpoint location (PostGIS Point)
     * @return List of team's locations within 50m of checkpoint
     */
    public List<LocationData> findTeamLocationsNearCheckpoint(UUID teamId, Point checkpointLocation) {
        return findTeamLocationsNearCheckpoint(teamId, checkpointLocation, DEFAULT_GEOFENCE_RADIUS_METERS);
    }

    /**
     * Placeholder for checkpoint approach detection logic.
     * Will notify admins when a team approaches a checkpoint within its zone.
     *
     * TODO: Implement checkpoint approach detection
     * - Track previous location state to detect new entries into geofence
     * - Create admin notification when team first approaches checkpoint
     * - Prevent duplicate notifications
     * - Consider team status (checked in, already completed checkpoint, etc.)
     *
     * @param teamId The team ID
     * @param checkpointLocation The checkpoint location
     * @param checkpointId The checkpoint ID (for reference)
     */
    public void checkAndNotifyCheckpointApproach(UUID teamId, Point checkpointLocation, UUID checkpointId) {
        // TODO: Implement admin notification logic
    }

    /**
     * Get the default geofence radius in meters.
     *
     * @return 50.0 meters
     */
    public double getGeofenceRadiusMeters() {
        return DEFAULT_GEOFENCE_RADIUS_METERS;
    }
}
