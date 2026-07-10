package nl.vlettenrally.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;

import nl.vlettenrally.data.domain.LocationData;
import nl.vlettenrally.data.domain.TeamDistanceSegment;
import nl.vlettenrally.data.repository.mock.MockLocationDataRepository;
import nl.vlettenrally.data.repository.mock.MockTeamDistanceSegmentRepository;
import nl.vlettenrally.service.util.GeoDistance;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.PrecisionModel;

/**
 * Unit tests for TeamDistanceCalculator. Uses the real in-memory mock repositories
 * so the segment-chain persistence (anchor = last segment endpoint) is exercised
 * end-to-end.
 */
class TeamDistanceCalculatorTest {

    private static final GeometryFactory GEOMETRY_FACTORY = new GeometryFactory(new PrecisionModel(), 4326);
    private static final double MIN_SEGMENT_METERS = 8.0;
    private static final UUID TEAM = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final UUID GAME = UUID.fromString("a1b2c3d4-e5f6-7890-abcd-ef1234567890");

    private MockLocationDataRepository locationRepo;
    private MockTeamDistanceSegmentRepository segmentRepo;
    private TeamDistanceCalculator calculator;
    private Instant base;

    @BeforeEach
    void setUp() {
        // The CDI @PostConstruct seed is not run by `new`, so the store starts empty.
        locationRepo = new MockLocationDataRepository();
        segmentRepo = new MockTeamDistanceSegmentRepository();

        calculator = new TeamDistanceCalculator();
        calculator.locationDataRepository = locationRepo;
        calculator.segmentRepository = segmentRepo;
        calculator.minSegmentMeters = MIN_SEGMENT_METERS;

        base = Instant.parse("2026-06-11T10:00:00Z");
    }

    private LocationData fix(double lon, double lat, int secondsFromBase) {
        LocationData fix = new LocationData();
        fix.setTeamId(TEAM);
        fix.setGameId(GAME);
        fix.setLocation(GEOMETRY_FACTORY.createPoint(new Coordinate(lon, lat)));
        fix.setLocationTime(base.plus(secondsFromBase, ChronoUnit.SECONDS));
        return fix;
    }

    /** Store a fix and fold it into the chain — mirrors the live ingest order. */
    private double ingest(double lon, double lat, int secondsFromBase) {
        LocationData f = fix(lon, lat, secondsFromBase);
        locationRepo.save(f);
        return calculator.accrueFix(TEAM, f);
    }

    @Test
    void firstFix_seedsChainWithoutAccruing() {
        assertEquals(0.0, ingest(4.9000, 52.3700, 0), 1e-9);

        List<TeamDistanceSegment> segments = segmentRepo.findByTeamId(TEAM);
        assertEquals(1, segments.size());
        assertTrue(segments.getFirst().isSeed());
        assertEquals(0.0, segments.getFirst().getMeters(), 1e-9);
    }

    @Test
    void stationaryJitter_accruesAboutZeroAndAddsNoSegments() {
        // A cluster where every fix stays within the threshold of the first (the anchor):
        // offsets here are < ~6 m, so the anchor never advances.
        ingest(4.90000, 52.37000, 0);
        ingest(4.90003, 52.36998, 30);
        ingest(4.89998, 52.37002, 60);
        ingest(4.90002, 52.37001, 90);
        double total = ingest(4.89999, 52.36999, 120);

        assertEquals(0.0, total, 1.0);
        assertEquals(1, segmentRepo.findByTeamId(TEAM).size(), "only the seed row should exist");
    }

    @Test
    void straightRun_accruesSumOfSegments() {
        // Five fixes ~22 m apart along a meridian — every step exceeds the threshold,
        // so all are counted and the total equals the summed segment lengths.
        double lon = 4.9000;
        double[] lats = {52.3700, 52.3702, 52.3704, 52.3706, 52.3708};
        double total = 0;
        for (int i = 0; i < lats.length; i++) {
            total = ingest(lon, lats[i], i * 30);
        }

        double expected = 0.0;
        for (int i = 1; i < lats.length; i++) {
            expected += GeoDistance.haversineMeters(lon, lats[i - 1], lon, lats[i]);
        }

        assertEquals(expected, total, 0.01);
        // Seed + one segment per counted step, chained from-time == predecessor to-time.
        List<TeamDistanceSegment> segments = segmentRepo.findByTeamId(TEAM);
        assertEquals(lats.length, segments.size());
        for (int i = 1; i < segments.size(); i++) {
            assertEquals(segments.get(i - 1).getToTime(), segments.get(i).getFromTime());
        }
    }

    @Test
    void slowDrift_countsOnceAnchorIsCrossed() {
        // Steps of ~5.6 m: each individual step is below the 8 m threshold, so a naive
        // per-segment filter would credit nothing. With a held anchor, distance accrues
        // every time the gap from the anchor crosses the threshold (here, every 2nd fix).
        double lon = 4.9000;
        double[] lats = {52.37000, 52.37005, 52.37010, 52.37015, 52.37020};
        double total = 0;
        for (int i = 0; i < lats.length; i++) {
            total = ingest(lon, lats[i], i * 30);
        }

        // Anchor walk: p0 seed; p1 (~5.6 m) held; p2 (~11.1 m) counted, anchor=p2;
        // p3 (~5.6 m) held; p4 (~11.1 m) counted, anchor=p4.
        double expected = GeoDistance.haversineMeters(lon, lats[0], lon, lats[2])
                        + GeoDistance.haversineMeters(lon, lats[2], lon, lats[4]);

        assertTrue(total > 0.0, "slow drift below per-step threshold should still accrue");
        assertEquals(expected, total, 0.01);
    }

    @Test
    void incrementalAccrualMatchesFullRecompute() {
        double lon = 4.9000;
        double[] lats = {52.3700, 52.3702, 52.3704, 52.3706, 52.3708, 52.3710, 52.3712, 52.3714};
        double incremental = 0;
        for (int i = 0; i < lats.length; i++) {
            incremental = ingest(lon, lats[i], i * 30);
        }

        // A fresh chain rebuilt from the stored fixes in one pass must reach the same total.
        TeamDistanceCalculator fullCalc = new TeamDistanceCalculator();
        fullCalc.locationDataRepository = locationRepo;
        fullCalc.segmentRepository = new MockTeamDistanceSegmentRepository();
        fullCalc.minSegmentMeters = MIN_SEGMENT_METERS;
        double fullPass = fullCalc.recomputeAfter(TEAM, Instant.EPOCH);

        assertEquals(fullPass, incremental, 1e-6);
    }

    @Test
    void nonAdvancingFix_isIgnored() {
        ingest(4.9000, 52.3700, 0);
        double total = ingest(4.9000, 52.3702, 30);

        // Same timestamp as the anchor (and an older one): skipped, chain untouched.
        double replayed = calculator.accrueFix(TEAM, fix(4.9000, 52.3710, 30));
        double older = calculator.accrueFix(TEAM, fix(4.9000, 52.3710, 10));

        assertEquals(total, replayed, 1e-9);
        assertEquals(total, older, 1e-9);
        assertEquals(2, segmentRepo.findByTeamId(TEAM).size());
    }

    @Test
    void recomputeAfter_countsPointsInsertedIntoAnOlderGap() {
        // A straight north-bound run at t=0 and t=60, accrued live (one seed + one segment).
        ingest(4.9000, 52.3700, 0);
        double incremental = ingest(4.9000, 52.3720, 60);
        assertTrue(incremental > 0);

        // Backfill inserts a fix in the gap and rebuilds the chain from it. The path length is
        // unchanged (the gap point lies on the straight line), so the total is preserved while
        // the spanning segment is replaced by two chained ones.
        LocationData gapFix = fix(4.9000, 52.3710, 30);
        locationRepo.save(gapFix);
        double recomputed = calculator.recomputeAfter(TEAM, gapFix.getLocationTime());
        assertEquals(incremental, recomputed, 1e-6);

        List<TeamDistanceSegment> segments = segmentRepo.findByTeamId(TEAM);
        assertEquals(3, segments.size(), "seed + two segments through the gap point");
        assertEquals(gapFix.getLocationTime(), segments.get(1).getToTime());

        // Idempotent: rebuilding the same tail again changes nothing.
        assertEquals(recomputed, calculator.recomputeAfter(TEAM, gapFix.getLocationTime()), 1e-9);
        assertEquals(3, segmentRepo.findByTeamId(TEAM).size());
    }

    @Test
    void recomputeAfter_neverDeletesCarryoverSeed() {
        // A migrated team: the carryover seed holds the pre-segment-table total and its
        // fixes may no longer exist in location_data. A rebuild from before the seed
        // must keep the carried meters as a floor.
        TeamDistanceSegment carryover = new TeamDistanceSegment();
        carryover.setTeamId(TEAM);
        carryover.setToTime(base);
        carryover.setToLon(4.9000);
        carryover.setToLat(52.3700);
        carryover.setMeters(1234.0);
        segmentRepo.save(carryover);

        double rebuilt = calculator.recomputeAfter(TEAM, base.minusSeconds(3600));
        assertEquals(1234.0, rebuilt, 1e-9);

        // Live accrual continues on top of the carried total, anchored at the seed endpoint.
        double afterMove = ingest(4.9000, 52.3702, 30);
        assertEquals(1234.0 + GeoDistance.haversineMeters(4.9000, 52.3700, 4.9000, 52.3702),
            afterMove, 0.01);
    }
}
