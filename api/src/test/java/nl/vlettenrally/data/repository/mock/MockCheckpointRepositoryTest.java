package nl.vlettenrally.data.repository.mock;

import nl.vlettenrally.data.domain.CircleCheckpoint;
import nl.vlettenrally.data.domain.PolygonCheckpoint;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.Point;
import org.locationtech.jts.geom.PrecisionModel;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Unit tests for the in-memory geofence containment in
 * {@link MockCheckpointRepository#findContainingPoint} — the implementation the dev/test
 * profiles use. Mirrors the JPA spatial query (circle by radius, polygon by containment).
 */
class MockCheckpointRepositoryTest {

    private static final GeometryFactory GEOMETRY_FACTORY = new GeometryFactory(new PrecisionModel(), 4326);

    private MockCheckpointRepository repository;
    private UUID gameId;

    @BeforeEach
    void setUp() {
        repository = new MockCheckpointRepository();
        repository.findAll().forEach(cp -> repository.delete(cp.getId())); // start from a clean slate
        gameId = UUID.randomUUID();
    }

    @Test
    void findContainingPoint_matchesCircleByRadius_andPolygonByContainment() {
        UUID circleId = saveCircle("In range", 52.10000, 5.10000, 50.0);
        saveCircle("Out of range", 52.20000, 5.20000, 50.0);
        UUID polygonId = savePolygon("Triangle",
            new Coordinate(5.0990, 52.0990),
            new Coordinate(5.1010, 52.0990),
            new Coordinate(5.1000, 52.1010));

        // A point ~30 m north of the circle centre and inside the triangle.
        List<UUID> hits = repository.findContainingPoint(gameId, point(52.10025, 5.10000));

        assertEquals(2, hits.size());
        assertTrue(hits.contains(circleId));
        assertTrue(hits.contains(polygonId));
    }

    @Test
    void findContainingPoint_includesHidden_excludesOtherGames() {
        // Hidden checkpoints stay off teams' map but must still drive automations,
        // so detection must return them.
        UUID hiddenId = saveCircle("Hidden", 52.10000, 5.10000, 50.0);
        repository.findById(hiddenId).ifPresent(cp -> {
            cp.setHidden(true);
            repository.save(cp);
        });
        // A checkpoint at the same spot but in a different game must never match.
        CircleCheckpoint otherGame = new CircleCheckpoint();
        otherGame.setGameId(UUID.randomUUID());
        otherGame.setLatitude(52.10000);
        otherGame.setLongitude(5.10000);
        otherGame.setRadiusMeters(50.0);
        repository.save(otherGame);

        List<UUID> hits = repository.findContainingPoint(gameId, point(52.10000, 5.10000));

        assertEquals(List.of(hiddenId), hits);
    }

    @Test
    void findCrossedBySegment_includesHidden() {
        // A hidden circle the segment passes straight through must still be returned.
        UUID hiddenId = saveCircle("Hidden", 52.10000, 5.10000, 50.0);
        repository.findById(hiddenId).ifPresent(cp -> {
            cp.setHidden(true);
            repository.save(cp);
        });

        List<UUID> crossing = repository.findCrossedBySegment(gameId,
            point(52.10000, 5.09900), point(52.10000, 5.10100));

        assertEquals(List.of(hiddenId), crossing);
    }

    @Test
    void findCrossedBySegment_circle_matchesWhenLinePassesThroughZone() {
        UUID circleId = saveCircle("On the line", 52.10000, 5.10000, 50.0);

        // Endpoints ~68 m east/west of the centre (both outside the 50 m radius) but the
        // line between them runs straight through the centre.
        List<UUID> crossing = repository.findCrossedBySegment(gameId,
            point(52.10000, 5.09900), point(52.10000, 5.10100));
        assertEquals(List.of(circleId), crossing);

        // A parallel line ~100 m north of the centre never enters the zone.
        List<UUID> beside = repository.findCrossedBySegment(gameId,
            point(52.10090, 5.09900), point(52.10090, 5.10100));
        assertTrue(beside.isEmpty());
    }

    @Test
    void findCrossedBySegment_polygon_matchesWhenLineIntersects() {
        UUID polygonId = savePolygon("Triangle",
            new Coordinate(5.0990, 52.0990),
            new Coordinate(5.1010, 52.0990),
            new Coordinate(5.1000, 52.1010));

        // A line through the middle of the triangle, endpoints outside it.
        List<UUID> crossing = repository.findCrossedBySegment(gameId,
            point(52.10000, 5.0980), point(52.10000, 5.1020));
        assertEquals(List.of(polygonId), crossing);

        // A line above the apex misses entirely.
        List<UUID> beside = repository.findCrossedBySegment(gameId,
            point(52.10300, 5.0980), point(52.10300, 5.1020));
        assertTrue(beside.isEmpty());
    }

    private UUID saveCircle(String name, double lat, double lng, double radius) {
        CircleCheckpoint cp = new CircleCheckpoint();
        cp.setGameId(gameId);
        cp.setName(name);
        cp.setLatitude(lat);
        cp.setLongitude(lng);
        cp.setRadiusMeters(radius);
        return repository.save(cp).getId();
    }

    private UUID savePolygon(String name, Coordinate... ring) {
        Coordinate[] closed = new Coordinate[ring.length + 1];
        System.arraycopy(ring, 0, closed, 0, ring.length);
        closed[ring.length] = ring[0];
        PolygonCheckpoint cp = new PolygonCheckpoint();
        cp.setGameId(gameId);
        cp.setName(name);
        cp.setZonePolygon(GEOMETRY_FACTORY.createPolygon(closed));
        return repository.save(cp).getId();
    }

    private static Point point(double lat, double lng) {
        return GEOMETRY_FACTORY.createPoint(new Coordinate(lng, lat));
    }
}
