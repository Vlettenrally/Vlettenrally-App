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
 *
 * Note: Geofencing trigger logic (when to notify admins) will be implemented later.
 * Currently provides helper methods for proximity detection.
 */
@ApplicationScoped
public class GeofencingService {

    private static final double GEOFENCE_RADIUS_METERS = 50.0;  // 50m radius around checkpoint

    @Inject
    LocationDataRepository locationDataRepository;

    /**
     * Check if a location is within the geofence boundary of a checkpoint.
     *
     * @param teamLocation The team's current location
     * @param checkpoint The checkpoint to check proximity to
     * @return true if team is within 50m of checkpoint
     */
    public boolean isWithinCheckpointGeofence(LocationData teamLocation, Checkpoint checkpoint) {
        if (teamLocation == null || teamLocation.getLocation() == null ||
            checkpoint == null || checkpoint.getLocation() == null) {
            return false;
        }

        // Delegate to PostGIS ST_DWithin with geography cast for accurate meter-based distance
        return locationDataRepository.isWithinDistance(
            teamLocation.getLocation(), checkpoint.getLocation(), GEOFENCE_RADIUS_METERS);
    }

    /**
     * Find all recent locations of a team within checkpoint's geofence.
     * Used to detect checkpoint approaches for admin notification.
     *
     * @param teamId The team ID
     * @param checkpointLocation The checkpoint location (PostGIS Point)
     * @return List of team's locations within 50m of checkpoint
     */
    public List<LocationData> findTeamLocationsNearCheckpoint(UUID teamId, Point checkpointLocation) {
        return locationDataRepository.findTeamLocationsWithinDistance(
            teamId,
            checkpointLocation,
            GEOFENCE_RADIUS_METERS
        );
    }

    /**
     * Placeholder for checkpoint approach detection logic.
     * Will notify admins when a team approaches a checkpoint within 50m radius.
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
        // Example flow:
        // 1. Check if team has recent locations within geofence
        // 2. Compare with previous state to detect NEW entry into geofence
        // 3. If new entry, create admin alert with team and checkpoint details
        // 4. Mark this checkpoint approach so we don't duplicate notifications
        // 5. Consider team status (should we notify if already checked in at this checkpoint?)
    }

    /**
     * Get the configured geofence radius in meters.
     *
     * @return 50.0 meters
     */
    public double getGeofenceRadiusMeters() {
        return GEOFENCE_RADIUS_METERS;
    }
}

