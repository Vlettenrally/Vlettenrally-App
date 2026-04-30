package nl.vlettenrally.service;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import nl.vlettenrally.data.domain.Checkpoint;
import nl.vlettenrally.data.domain.CircleCheckpoint;
import nl.vlettenrally.data.domain.LocationData;
import nl.vlettenrally.data.domain.PolygonCheckpoint;
import nl.vlettenrally.data.repository.LocationDataRepository;
import org.locationtech.jts.geom.Point;

import java.util.List;
import java.util.UUID;

/**
 * Service for geofencing operations.
 * Detects when teams approach checkpoints and triggers notifications.
 * Uses polymorphic checkpoint types for zone-specific containment logic.
 */
@ApplicationScoped
public class GeofencingService {

    private static final double DEFAULT_GEOFENCE_RADIUS_METERS = 50.0;

    @Inject
    LocationDataRepository locationDataRepository;

    /**
     * Check if a location is within the geofence boundary of a checkpoint.
     * Dispatches to the appropriate containment check based on checkpoint type.
     *
     * @param teamLocation The team's current location
     * @param checkpoint The checkpoint to check proximity to
     * @return true if team is within the checkpoint's zone
     */
    public boolean isWithinCheckpointGeofence(LocationData teamLocation, Checkpoint checkpoint) {
        if (teamLocation == null || teamLocation.getLocation() == null || checkpoint == null) {
            return false;
        }

        return switch (checkpoint) {
            case PolygonCheckpoint poly -> isWithinPolygon(teamLocation, poly);
            case CircleCheckpoint circle -> isWithinCircle(teamLocation, circle);
        };
    }

    private boolean isWithinCircle(LocationData teamLocation, CircleCheckpoint checkpoint) {
        if (checkpoint.getLocation() == null) {
            return false;
        }
        double radius = checkpoint.getRadiusMeters() > 0
            ? checkpoint.getRadiusMeters()
            : DEFAULT_GEOFENCE_RADIUS_METERS;
        return locationDataRepository.isWithinDistance(
            teamLocation.getLocation(), checkpoint.getLocation(), radius);
    }

    private boolean isWithinPolygon(LocationData teamLocation, PolygonCheckpoint checkpoint) {
        if (checkpoint.getZonePolygon() == null) {
            return false;
        }
        return checkpoint.getZonePolygon().contains(teamLocation.getLocation());
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
