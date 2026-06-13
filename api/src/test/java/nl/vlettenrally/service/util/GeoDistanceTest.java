package nl.vlettenrally.service.util;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.Point;
import org.locationtech.jts.geom.PrecisionModel;

class GeoDistanceTest {

    private static final GeometryFactory GEOMETRY_FACTORY = new GeometryFactory(new PrecisionModel(), 4326);

    @Test
    void haversineMeters_samePoint_isZero() {
        assertEquals(0.0, GeoDistance.haversineMeters(4.9, 52.37, 4.9, 52.37), 1e-6);
    }

    @Test
    void haversineMeters_oneDegreeLatitude_isAboutMeridianLength() {
        // One degree of latitude is ~111.2 km along a meridian, anywhere on Earth.
        double d = GeoDistance.haversineMeters(4.9, 52.0, 4.9, 53.0);
        assertEquals(111_195.0, d, 200.0);
    }

    @Test
    void haversineMeters_pointOverload_matchesScalarOverload() {
        Point a = GEOMETRY_FACTORY.createPoint(new Coordinate(4.9000, 52.3700));
        Point b = GEOMETRY_FACTORY.createPoint(new Coordinate(4.9020, 52.3710));
        assertEquals(
                GeoDistance.haversineMeters(4.9000, 52.3700, 4.9020, 52.3710),
                GeoDistance.haversineMeters(a, b),
                1e-9);
    }

    @Test
    void bearingDegrees_cardinalDirections() {
        // From a base point, head a small step toward each cardinal direction.
        assertEquals(0.0, GeoDistance.bearingDegrees(4.9, 52.0, 4.9, 52.001), 0.5);   // north
        assertEquals(90.0, GeoDistance.bearingDegrees(4.9, 52.0, 4.901, 52.0), 0.5);  // east
        assertEquals(180.0, GeoDistance.bearingDegrees(4.9, 52.0, 4.9, 51.999), 0.5); // south
        assertEquals(270.0, GeoDistance.bearingDegrees(4.9, 52.0, 4.899, 52.0), 0.5); // west
    }

    @Test
    void turnAngleDegrees_isSmallestDifferenceAndSymmetric() {
        assertEquals(20.0, GeoDistance.turnAngleDegrees(10.0, 350.0), 1e-9);
        assertEquals(20.0, GeoDistance.turnAngleDegrees(350.0, 10.0), 1e-9);
        assertEquals(180.0, GeoDistance.turnAngleDegrees(0.0, 180.0), 1e-9);
        assertEquals(0.0, GeoDistance.turnAngleDegrees(90.0, 90.0), 1e-9);
    }
}
