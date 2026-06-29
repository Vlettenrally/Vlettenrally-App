package nl.vlettenrally.service.util;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.math.BigDecimal;
import java.time.Instant;

import nl.vlettenrally.data.domain.LocationData;
import org.junit.jupiter.api.Test;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.PrecisionModel;

/**
 * Pure unit tests for {@link TrackOutlierFilter}. Coordinates are around lat 52° where
 * 0.001° longitude ≈ 68.7 m, used to dial in implied speeds and turn angles.
 */
class TrackOutlierFilterTest {

    private static final GeometryFactory GEOMETRY_FACTORY = new GeometryFactory(new PrecisionModel(), 4326);
    private static final Instant T0 = Instant.parse("2026-06-13T10:00:00Z");

    private static final TrackOutlierFilter.Thresholds THRESHOLDS = new TrackOutlierFilter.Thresholds(
        5,      // minIntervalSeconds
        50.0,   // maxAccuracyMeters
        120.0,  // maxSpeedKmh
        25.0,   // boatMaxSpeedKmh
        120.0,  // turnAngleThresholdDegrees
        10.0,   // minBearingSegmentMeters
        300);   // staleFixSeconds

    @Test
    void straightRunAtBoatSpeed_isAccepted() {
        LocationData prev = at(52.0, 5.000, 0);
        LocationData last = at(52.0, 5.001, 60);   // ~69 m east, ~4 km/h
        LocationData cand = at(52.0, 5.002, 120);  // continue east, same speed
        assertEquals(TrackOutlierFilter.Decision.ACCEPT, evaluate(prev, last, cand));
    }

    @Test
    void genuineSharpTurnAtBoatSpeed_isAccepted() {
        LocationData prev = at(52.0, 5.000, 0);
        LocationData last = at(52.0, 5.001, 60);
        LocationData cand = at(52.0, 5.000, 120);  // U-turn back west, still ~4 km/h
        assertEquals(TrackOutlierFilter.Decision.ACCEPT, evaluate(prev, last, cand));
    }

    @Test
    void sharpReversalAtHighSpeed_isDroppedAsSpike() {
        LocationData prev = at(52.0, 5.000, 0);
        LocationData last = at(52.0, 5.001, 60);
        LocationData cand = at(52.0, 4.991, 120);  // ~687 m west in 60s ≈ 41 km/h, turn 180°
        assertEquals(TrackOutlierFilter.Decision.DROP_SPIKE, evaluate(prev, last, cand));
    }

    @Test
    void straightLineTeleport_isDroppedAsTeleport() {
        LocationData last = at(52.0, 5.000, 60);
        LocationData cand = at(52.0, 5.050, 70);   // ~3.4 km in 10s — far over 120 km/h
        assertEquals(TrackOutlierFilter.Decision.DROP_TELEPORT, evaluate(null, last, cand));
    }

    @Test
    void sharpTurnWithSubMeterLegs_isNotSpike() {
        // Moored jitter: both legs are far below minBearingSegmentMeters, so the bearing is
        // meaningless and the turn test is skipped — implied speed stays tiny → accept.
        LocationData prev = at(52.0, 5.00000, 0);
        LocationData last = at(52.0, 5.00005, 60);  // ~3.4 m
        LocationData cand = at(52.0, 4.99995, 120); // ~3.4 m back, turn 180°
        assertEquals(TrackOutlierFilter.Decision.ACCEPT, evaluate(prev, last, cand));
    }

    @Test
    void poorAccuracy_isDropped() {
        LocationData last = at(52.0, 5.000, 0);
        LocationData cand = at(52.0, 5.0001, 60, new BigDecimal("100.0"));
        assertEquals(TrackOutlierFilter.Decision.DROP_ACCURACY, evaluate(null, last, cand));
    }

    @Test
    void nullAccuracy_passesAccuracyGate() {
        LocationData last = at(52.0, 5.000, 0);
        LocationData cand = at(52.0, 5.0001, 60);  // null accuracy, small move
        assertEquals(TrackOutlierFilter.Decision.ACCEPT, evaluate(null, last, cand));
    }

    @Test
    void belowMinInterval_isThrottled() {
        LocationData last = at(52.0, 5.000, 60);
        LocationData cand = at(52.0, 5.0001, 62);  // only 2s later
        assertEquals(TrackOutlierFilter.Decision.DROP_THROTTLE, evaluate(null, last, cand));
    }

    @Test
    void staleGap_bypassesBoatRulesButKeepsTeleportCeiling() {
        LocationData last = at(52.0, 5.000, 0);
        // After a 10-min gap a legitimate ~1.4 km catch-up (~8 km/h) is accepted...
        LocationData reacquire = at(52.0, 5.020, 600);
        assertEquals(TrackOutlierFilter.Decision.ACCEPT, evaluate(null, last, reacquire));
        // ...but a true teleport still trips the hard ceiling even when stale.
        LocationData teleport = at(52.0, 6.000, 600);
        assertEquals(TrackOutlierFilter.Decision.DROP_TELEPORT, evaluate(null, last, teleport));
    }

    private static TrackOutlierFilter.Decision evaluate(LocationData prev, LocationData last, LocationData cand) {
        return TrackOutlierFilter.evaluate(prev, last, cand, THRESHOLDS).decision();
    }

    private static LocationData at(double lat, double lon, long secondsFromT0) {
        return at(lat, lon, secondsFromT0, null);
    }

    private static LocationData at(double lat, double lon, long secondsFromT0, BigDecimal accuracy) {
        LocationData data = new LocationData();
        data.setLocation(GEOMETRY_FACTORY.createPoint(new Coordinate(lon, lat)));
        data.setLocationTime(T0.plusSeconds(secondsFromT0));
        data.setAccuracy(accuracy);
        return data;
    }
}
