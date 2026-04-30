package nl.vlettenrally.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import nl.vlettenrally.data.domain.CircleCheckpoint;
import nl.vlettenrally.data.domain.PolygonCheckpoint;
import nl.vlettenrally.data.domain.LocationData;
import nl.vlettenrally.data.repository.LocationDataRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.Point;
import org.locationtech.jts.geom.Polygon;
import org.locationtech.jts.geom.PrecisionModel;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * Unit tests for GeofencingService.
 * Verifies that proximity checks delegate to PostGIS via the repository,
 * and that polygon zone types use JTS containment logic.
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

    //Circle zone type (default)

    @Test
    void isWithinCheckpointGeofence_circleWithinRadius_shouldReturnTrue() {
        LocationData teamLocation = new LocationData();
        teamLocation.setLocation(teamPoint);

        CircleCheckpoint checkpoint = new CircleCheckpoint();
        checkpoint.setLocation(checkpointPoint);
        // default radiusMeters = 50

        when(locationDataRepository.isWithinDistance(teamPoint, checkpointPoint, 50.0))
                .thenReturn(true);

        assertTrue(geofencingService.isWithinCheckpointGeofence(teamLocation, checkpoint));
        verify(locationDataRepository).isWithinDistance(teamPoint, checkpointPoint, 50.0);
    }

    @Test
    void isWithinCheckpointGeofence_circleOutsideRadius_shouldReturnFalse() {
        LocationData teamLocation = new LocationData();
        teamLocation.setLocation(teamPoint);

        CircleCheckpoint checkpoint = new CircleCheckpoint();
        checkpoint.setLocation(checkpointPoint);

        when(locationDataRepository.isWithinDistance(teamPoint, checkpointPoint, 50.0))
                .thenReturn(false);

        assertFalse(geofencingService.isWithinCheckpointGeofence(teamLocation, checkpoint));
    }

    @Test
    void isWithinCheckpointGeofence_customRadius_shouldUseConfiguredValue() {
        LocationData teamLocation = new LocationData();
        teamLocation.setLocation(teamPoint);

        CircleCheckpoint checkpoint = new CircleCheckpoint(200.0);
        checkpoint.setLocation(checkpointPoint);

        when(locationDataRepository.isWithinDistance(teamPoint, checkpointPoint, 200.0))
                .thenReturn(true);

        assertTrue(geofencingService.isWithinCheckpointGeofence(teamLocation, checkpoint));
        verify(locationDataRepository).isWithinDistance(teamPoint, checkpointPoint, 200.0);
    }

    @Test
    void isWithinCheckpointGeofence_nullTeamLocation_shouldReturnFalse() {
        CircleCheckpoint checkpoint = new CircleCheckpoint();
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

        CircleCheckpoint checkpoint = new CircleCheckpoint();
        checkpoint.setLocation(checkpointPoint);

        assertFalse(geofencingService.isWithinCheckpointGeofence(teamLocation, checkpoint));
        verifyNoInteractions(locationDataRepository);
    }

    @Test
    void isWithinCheckpointGeofence_nullCheckpointLocation_shouldReturnFalse() {
        LocationData teamLocation = new LocationData();
        teamLocation.setLocation(teamPoint);

        CircleCheckpoint checkpoint = new CircleCheckpoint();
        // location is null

        assertFalse(geofencingService.isWithinCheckpointGeofence(teamLocation, checkpoint));
        verifyNoInteractions(locationDataRepository);
    }

    //Polygon zone type

    private Polygon createTestPolygon() {
        // Rectangle-like polygon around the test area
        Coordinate[] coords = new Coordinate[] {
            new Coordinate(4.8950, 52.3800),  // NW (lng, lat)
            new Coordinate(4.9050, 52.3800),  // NE
            new Coordinate(4.9050, 52.3770),  // SE
            new Coordinate(4.8950, 52.3770),  // SW
            new Coordinate(4.8950, 52.3800),  // close ring
        };
        return GEOMETRY_FACTORY.createPolygon(coords);
    }

    @Test
    void isWithinCheckpointGeofence_polygonInside_shouldReturnTrue() {
        // Team at 52.3782, 4.9003 — inside the polygon
        LocationData teamLocation = new LocationData();
        teamLocation.setLocation(teamPoint);

        PolygonCheckpoint checkpoint = new PolygonCheckpoint(createTestPolygon());

        assertTrue(geofencingService.isWithinCheckpointGeofence(teamLocation, checkpoint));
        verifyNoInteractions(locationDataRepository); // polygon doesn't use PostGIS distance
    }

    @Test
    void isWithinCheckpointGeofence_polygonOutside_shouldReturnFalse() {
        // Team at 52.3782, 4.9003 — outside the polygon (east boundary too tight)
        LocationData teamLocation = new LocationData();
        teamLocation.setLocation(teamPoint);

        Coordinate[] coords = new Coordinate[] {
            new Coordinate(4.8950, 52.3800),
            new Coordinate(4.9001, 52.3800),  // east at 4.9001, team lng is 4.9003
            new Coordinate(4.9001, 52.3770),
            new Coordinate(4.8950, 52.3770),
            new Coordinate(4.8950, 52.3800),
        };
        Polygon tightPolygon = GEOMETRY_FACTORY.createPolygon(coords);

        PolygonCheckpoint checkpoint = new PolygonCheckpoint(tightPolygon);

        assertFalse(geofencingService.isWithinCheckpointGeofence(teamLocation, checkpoint));
    }

    @Test
    void isWithinCheckpointGeofence_polygonNull_shouldReturnFalse() {
        LocationData teamLocation = new LocationData();
        teamLocation.setLocation(teamPoint);

        PolygonCheckpoint checkpoint = new PolygonCheckpoint();
        // zonePolygon is null

        assertFalse(geofencingService.isWithinCheckpointGeofence(teamLocation, checkpoint));
    }

    @Test
    void getGeofenceRadiusMeters_shouldReturn50() {
        assertEquals(50.0, geofencingService.getGeofenceRadiusMeters());
    }
}
