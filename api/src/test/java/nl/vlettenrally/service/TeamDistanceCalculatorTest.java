package nl.vlettenrally.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

import nl.vlettenrally.data.domain.LocationData;
import nl.vlettenrally.data.repository.mock.MockLocationDataRepository;
import nl.vlettenrally.data.repository.mock.MockTeamDistanceProgressRepository;
import nl.vlettenrally.service.util.GeoDistance;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.PrecisionModel;

/**
 * Unit tests for TeamDistanceCalculator. Uses the real in-memory mock repositories
 * so the anchor/cursor persistence and incremental reads are exercised end-to-end.
 */
class TeamDistanceCalculatorTest {

    private static final GeometryFactory GEOMETRY_FACTORY = new GeometryFactory(new PrecisionModel(), 4326);
    private static final double MIN_SEGMENT_METERS = 8.0;
    private static final UUID TEAM = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final UUID GAME = UUID.fromString("a1b2c3d4-e5f6-7890-abcd-ef1234567890");

    private MockLocationDataRepository locationRepo;
    private TeamDistanceCalculator calculator;
    private Instant base;

    @BeforeEach
    void setUp() {
        // The CDI @PostConstruct seed is not run by `new`, so the store starts empty.
        locationRepo = new MockLocationDataRepository();

        calculator = new TeamDistanceCalculator();
        calculator.locationDataRepository = locationRepo;
        calculator.progressRepository = new MockTeamDistanceProgressRepository();
        calculator.minSegmentMeters = MIN_SEGMENT_METERS;

        base = Instant.parse("2026-06-11T10:00:00Z");
    }

    /** Save a fix for TEAM at the given lon/lat, `secondsFromBase` after the base time. */
    private void saveFix(double lon, double lat, int secondsFromBase) {
        LocationData fix = new LocationData();
        fix.setTeamId(TEAM);
        fix.setGameId(GAME);
        fix.setLocation(GEOMETRY_FACTORY.createPoint(new Coordinate(lon, lat)));
        fix.setLocationTime(base.plus(secondsFromBase, ChronoUnit.SECONDS));
        locationRepo.save(fix);
    }

    @Test
    void singleFix_accruesNothing() {
        saveFix(4.9000, 52.3700, 0);
        assertEquals(0.0, calculator.computeTraveledMeters(TEAM), 1e-9);
    }

    @Test
    void noFixes_accruesNothing() {
        assertEquals(0.0, calculator.computeTraveledMeters(TEAM), 1e-9);
    }

    @Test
    void stationaryJitter_accruesAboutZero() {
        // A cluster where every fix stays within the threshold of the first (the anchor):
        // offsets here are < ~6 m, so the anchor never advances.
        saveFix(4.90000, 52.37000, 0);
        saveFix(4.90003, 52.36998, 30);
        saveFix(4.89998, 52.37002, 60);
        saveFix(4.90002, 52.37001, 90);
        saveFix(4.89999, 52.36999, 120);

        assertEquals(0.0, calculator.computeTraveledMeters(TEAM), 1.0);
    }

    @Test
    void straightRun_accruesSumOfSegments() {
        // Five fixes ~22 m apart along a meridian — every step exceeds the threshold,
        // so all are counted and the total equals the summed segment lengths.
        double lon = 4.9000;
        double[] lats = {52.3700, 52.3702, 52.3704, 52.3706, 52.3708};
        for (int i = 0; i < lats.length; i++) {
            saveFix(lon, lats[i], i * 30);
        }

        double expected = 0.0;
        for (int i = 1; i < lats.length; i++) {
            expected += GeoDistance.haversineMeters(lon, lats[i - 1], lon, lats[i]);
        }

        assertEquals(expected, calculator.computeTraveledMeters(TEAM), 0.01);
    }

    @Test
    void slowDrift_countsOnceAnchorIsCrossed() {
        // Steps of ~5.6 m: each individual step is below the 8 m threshold, so a naive
        // per-segment filter would credit nothing. With a held anchor, distance accrues
        // every time the gap from the anchor crosses the threshold (here, every 2nd fix).
        double lon = 4.9000;
        double[] lats = {52.37000, 52.37005, 52.37010, 52.37015, 52.37020};
        for (int i = 0; i < lats.length; i++) {
            saveFix(lon, lats[i], i * 30);
        }

        // Anchor walk: p0 anchor; p1 (~5.6 m) held; p2 (~11.1 m) counted, anchor=p2;
        // p3 (~5.6 m) held; p4 (~11.1 m) counted, anchor=p4.
        double expected = GeoDistance.haversineMeters(lon, lats[0], lon, lats[2])
                        + GeoDistance.haversineMeters(lon, lats[2], lon, lats[4]);

        double actual = calculator.computeTraveledMeters(TEAM);
        assertTrue(actual > 0.0, "slow drift below per-step threshold should still accrue");
        assertEquals(expected, actual, 0.01);
    }

    @Test
    void incrementalReadsMatchFullComputation_andAreMonotonic() {
        double lon = 4.9000;
        double[] lats = {52.3700, 52.3702, 52.3704, 52.3706, 52.3708, 52.3710, 52.3712, 52.3714};

        // First batch: fixes 0..3.
        for (int i = 0; i < 4; i++) {
            saveFix(lon, lats[i], i * 30);
        }
        double afterFirst = calculator.computeTraveledMeters(TEAM);

        // Second batch: fixes 4..7 (strictly newer) — only these should be read now.
        for (int i = 4; i < lats.length; i++) {
            saveFix(lon, lats[i], i * 30);
        }
        double afterSecond = calculator.computeTraveledMeters(TEAM);

        assertTrue(afterSecond > afterFirst, "appending fixes must not lower the cumulative");

        // A fresh calculator over the full track in one pass must reach the same total.
        MockLocationDataRepository fullRepo = new MockLocationDataRepository();
        TeamDistanceCalculator fullCalc = new TeamDistanceCalculator();
        fullCalc.locationDataRepository = fullRepo;
        fullCalc.progressRepository = new MockTeamDistanceProgressRepository();
        fullCalc.minSegmentMeters = MIN_SEGMENT_METERS;
        for (int i = 0; i < lats.length; i++) {
            LocationData fix = new LocationData();
            fix.setTeamId(TEAM);
            fix.setGameId(GAME);
            fix.setLocation(GEOMETRY_FACTORY.createPoint(new Coordinate(lon, lats[i])));
            fix.setLocationTime(base.plus(i * 30, ChronoUnit.SECONDS));
            fullRepo.save(fix);
        }
        double fullPass = fullCalc.computeTraveledMeters(TEAM);

        assertEquals(fullPass, afterSecond, 1e-6);
    }

    @Test
    void noNewFixes_returnsUnchangedCumulative() {
        saveFix(4.9000, 52.3700, 0);
        saveFix(4.9000, 52.3702, 30);
        double first = calculator.computeTraveledMeters(TEAM);
        double second = calculator.computeTraveledMeters(TEAM); // nothing new
        assertEquals(first, second, 1e-9);
    }

    @Test
    void recomputeFromScratch_countsPointsInsertedIntoAnOlderGap() {
        // A straight north-bound run at t=0 and t=60, processed incrementally (cursor advances to t=60).
        saveFix(4.9000, 52.3700, 0);
        saveFix(4.9000, 52.3720, 60);
        double incremental = calculator.computeTraveledMeters(TEAM);
        assertTrue(incremental > 0);

        // Backfill inserts a fix in the gap. It predates the cursor, so a plain incremental pass
        // ignores it entirely...
        saveFix(4.9000, 52.3710, 30);
        assertEquals(incremental, calculator.computeTraveledMeters(TEAM), 1e-9);

        // ...but recomputeFromScratch re-walks the whole track. The path length is unchanged (the
        // gap point lies on the straight line), so it equals the incremental total, proving the
        // full re-walk happened rather than skipping the older insert.
        double recomputed = calculator.recomputeFromScratch(TEAM);
        assertEquals(incremental, recomputed, 1e-6);
    }

    @Test
    void recomputeFromScratch_clampsUpToPreviousTotalToStayMonotonic() {
        // Seed a single fix (a real recompute would yield 0 metres) but pretend a larger cumulative
        // was already credited. The clamp must not let the fed value drop below that.
        saveFix(4.9000, 52.3700, 0);
        calculator.computeTraveledMeters(TEAM);
        MockTeamDistanceProgressRepository progressRepo =
            (MockTeamDistanceProgressRepository) calculator.progressRepository;
        var progress = progressRepo.findByTeamId(TEAM).orElseThrow();
        progress.setCumulativeMeters(1234.0);
        progressRepo.save(progress);

        double recomputed = calculator.recomputeFromScratch(TEAM);
        assertEquals(1234.0, recomputed, 1e-9);
        assertEquals(1234.0, progressRepo.findByTeamId(TEAM).orElseThrow().getCumulativeMeters(), 1e-9);
    }
}
