package nl.vlettenrally.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import nl.vlettenrally.data.domain.Checkpoint;
import nl.vlettenrally.data.domain.LocationData;
import nl.vlettenrally.data.repository.LocationDataRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.Point;
import org.locationtech.jts.geom.PrecisionModel;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * Unit tests for GeofencingService.
 * Verifies that proximity checks delegate to PostGIS via the repository.
 */
@ExtendWith(MockitoExtension.class)
class GeofencingServiceTest {

    @Mock
    LocationDataRepository locationDataRepository;

    @InjectMocks
    GeofencingService geofencingService;

    private static final GeometryFactory GEOMETRY_FACTORY = new GeometryFactory(new PrecisionModel(), 4326);

    private Point checkpointPoint;
    private Point teamPoint;

    @BeforeEach
    void setUp() {
        // Amsterdam Central Station area
        checkpointPoint = GEOMETRY_FACTORY.createPoint(new Coordinate(4.9000, 52.3780));
        teamPoint = GEOMETRY_FACTORY.createPoint(new Coordinate(4.9003, 52.3782));
    }

    @Test
    void isWithinCheckpointGeofence_withinRadius_shouldReturnTrue() {
        LocationData teamLocation = new LocationData();
        teamLocation.setLocation(teamPoint);

        Checkpoint checkpoint = new Checkpoint();
        checkpoint.setLocation(checkpointPoint);

        when(locationDataRepository.isWithinDistance(teamPoint, checkpointPoint, 50.0))
                .thenReturn(true);

        assertTrue(geofencingService.isWithinCheckpointGeofence(teamLocation, checkpoint));
        verify(locationDataRepository).isWithinDistance(teamPoint, checkpointPoint, 50.0);
    }

    @Test
    void isWithinCheckpointGeofence_outsideRadius_shouldReturnFalse() {
        LocationData teamLocation = new LocationData();
        teamLocation.setLocation(teamPoint);

        Checkpoint checkpoint = new Checkpoint();
        checkpoint.setLocation(checkpointPoint);

        when(locationDataRepository.isWithinDistance(teamPoint, checkpointPoint, 50.0))
                .thenReturn(false);

        assertFalse(geofencingService.isWithinCheckpointGeofence(teamLocation, checkpoint));
        verify(locationDataRepository).isWithinDistance(teamPoint, checkpointPoint, 50.0);
    }

    @Test
    void isWithinCheckpointGeofence_nullTeamLocation_shouldReturnFalse() {
        Checkpoint checkpoint = new Checkpoint();
        checkpoint.setLocation(checkpointPoint);

        assertFalse(geofencingService.isWithinCheckpointGeofence(null, checkpoint));
        verifyNoInteractions(locationDataRepository);
    }

    @Test
    void isWithinCheckpointGeofence_nullCheckpoint_shouldReturnFalse() {
        LocationData teamLocation = new LocationData();
        teamLocation.setLocation(teamPoint);

        assertFalse(geofencingService.isWithinCheckpointGeofence(teamLocation, null));
        verifyNoInteractions(locationDataRepository);
    }

    @Test
    void isWithinCheckpointGeofence_nullTeamLocationPoint_shouldReturnFalse() {
        LocationData teamLocation = new LocationData();
        // location is null

        Checkpoint checkpoint = new Checkpoint();
        checkpoint.setLocation(checkpointPoint);

        assertFalse(geofencingService.isWithinCheckpointGeofence(teamLocation, checkpoint));
        verifyNoInteractions(locationDataRepository);
    }

    @Test
    void isWithinCheckpointGeofence_nullCheckpointLocation_shouldReturnFalse() {
        LocationData teamLocation = new LocationData();
        teamLocation.setLocation(teamPoint);

        Checkpoint checkpoint = new Checkpoint();
        // location is null

        assertFalse(geofencingService.isWithinCheckpointGeofence(teamLocation, checkpoint));
        verifyNoInteractions(locationDataRepository);
    }

    @Test
    void isWithinCheckpointGeofence_justInsideRadius_shouldReturnTrue() {
        // Team location approximately 44m north of checkpoint (just inside the 50m boundary)
        Point nearbyPoint = GEOMETRY_FACTORY.createPoint(new Coordinate(4.9000, 52.37840));
        LocationData teamLocation = new LocationData();
        teamLocation.setLocation(nearbyPoint);

        Checkpoint checkpoint = new Checkpoint();
        checkpoint.setLocation(checkpointPoint);

        when(locationDataRepository.isWithinDistance(nearbyPoint, checkpointPoint, 50.0))
                .thenReturn(true);

        assertTrue(geofencingService.isWithinCheckpointGeofence(teamLocation, checkpoint));
        verify(locationDataRepository).isWithinDistance(nearbyPoint, checkpointPoint, 50.0);
    }

    @Test
    void isWithinCheckpointGeofence_justOutsideRadius_shouldReturnFalse() {
        // Team location approximately 56m north of checkpoint (just outside the 50m boundary)
        Point farPoint = GEOMETRY_FACTORY.createPoint(new Coordinate(4.9000, 52.37850));
        LocationData teamLocation = new LocationData();
        teamLocation.setLocation(farPoint);

        Checkpoint checkpoint = new Checkpoint();
        checkpoint.setLocation(checkpointPoint);

        when(locationDataRepository.isWithinDistance(farPoint, checkpointPoint, 50.0))
                .thenReturn(false);

        assertFalse(geofencingService.isWithinCheckpointGeofence(teamLocation, checkpoint));
        verify(locationDataRepository).isWithinDistance(farPoint, checkpointPoint, 50.0);
    }

    @Test
    void getGeofenceRadiusMeters_shouldReturn50() {
        assertEquals(50.0, geofencingService.getGeofenceRadiusMeters());
    }
}



