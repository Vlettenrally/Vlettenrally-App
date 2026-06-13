package nl.vlettenrally.data.repository.mock;

import io.quarkus.arc.profile.IfBuildProfile;
import io.quarkus.logging.Log;
import jakarta.annotation.PostConstruct;
import jakarta.enterprise.context.ApplicationScoped;
import nl.vlettenrally.data.domain.Checkpoint;
import nl.vlettenrally.data.domain.CircleCheckpoint;
import nl.vlettenrally.data.domain.PolygonCheckpoint;
import nl.vlettenrally.data.repository.CheckpointRepository;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.Point;
import org.locationtech.jts.geom.PrecisionModel;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * Mock implementation of CheckpointRepository with prefilled data.
 * Only active in dev and test profiles - replaced by PostgresCheckpointRepository in prod.
 */
@IfBuildProfile(anyOf = {"dev", "test"})
@ApplicationScoped
public class MockCheckpointRepository implements CheckpointRepository {

    private static final GeometryFactory GEOMETRY_FACTORY = new GeometryFactory(new PrecisionModel(), 4326);

    private final Map<UUID, Checkpoint> checkpoints = new ConcurrentHashMap<>();

    // Well-known UUIDs for consistent cross-reference
    public static final UUID CHECKPOINT_START_ID = UUID.fromString("c0000001-0000-0000-0000-000000000001");
    public static final UUID CHECKPOINT_AMSTEL_ID = UUID.fromString("c0000002-0000-0000-0000-000000000002");
    public static final UUID CHECKPOINT_PRINSEN_ID = UUID.fromString("c0000003-0000-0000-0000-000000000003");
    public static final UUID CHECKPOINT_KEIZER_ID = UUID.fromString("c0000004-0000-0000-0000-000000000004");
    public static final UUID CHECKPOINT_FINISH_ID = UUID.fromString("c0000005-0000-0000-0000-000000000005");

    @PostConstruct
    void init() {
        initializeData();
    }

    public void initializeData() {
        checkpoints.clear();
        
        Instant now = Instant.now();

        // Checkpoint 1 - Start
        Checkpoint cp1 = new CircleCheckpoint();
        cp1.setId(CHECKPOINT_START_ID);
        cp1.setGameId(MockGameRepository.GAME_2026_ID);
        cp1.setName("Start - Havenkade");
        cp1.setOpeningTime(now.minus(2, ChronoUnit.HOURS));
        cp1.setClosingTime(now.plus(4, ChronoUnit.HOURS));
        cp1.setPoints(0);
        cp1.setLatitude(52.3676);
        cp1.setLongitude(4.9041);
        checkpoints.put(cp1.getId(), cp1);

        // Checkpoint 2 - Amstel
        Checkpoint cp2 = new CircleCheckpoint();
        cp2.setId(CHECKPOINT_AMSTEL_ID);
        cp2.setGameId(MockGameRepository.GAME_2026_ID);
        cp2.setName("Checkpoint Amstel");
        cp2.setOpeningTime(now.minus(90, ChronoUnit.MINUTES));
        cp2.setClosingTime(now.plus(3, ChronoUnit.HOURS));
        cp2.setPoints(100);
        cp2.setLatitude(52.3580);
        cp2.setLongitude(4.9000);
        checkpoints.put(cp2.getId(), cp2);

        // Checkpoint 3 - Prinsengracht
        Checkpoint cp3 = new CircleCheckpoint();
        cp3.setId(CHECKPOINT_PRINSEN_ID);
        cp3.setGameId(MockGameRepository.GAME_2026_ID);
        cp3.setName("Checkpoint Prinsengracht");
        cp3.setOpeningTime(now.minus(1, ChronoUnit.HOURS));
        cp3.setClosingTime(now.plus(150, ChronoUnit.MINUTES));
        cp3.setPoints(150);
        cp3.setLatitude(52.3738);
        cp3.setLongitude(4.8820);
        checkpoints.put(cp3.getId(), cp3);

        // Checkpoint 4 - Keizersgracht
        Checkpoint cp4 = new CircleCheckpoint();
        cp4.setId(CHECKPOINT_KEIZER_ID);
        cp4.setGameId(MockGameRepository.GAME_2026_ID);
        cp4.setName("Checkpoint Keizersgracht");
        cp4.setOpeningTime(now.minus(30, ChronoUnit.MINUTES));
        cp4.setClosingTime(now.plus(2, ChronoUnit.HOURS));
        cp4.setPoints(200);
        cp4.setLatitude(52.3702);
        cp4.setLongitude(4.8872);
        checkpoints.put(cp4.getId(), cp4);

        // Checkpoint 5 - Finish
        Checkpoint cp5 = new CircleCheckpoint();
        cp5.setId(CHECKPOINT_FINISH_ID);
        cp5.setGameId(MockGameRepository.GAME_2026_ID);
        cp5.setName("Finish - Centraal Station");
        cp5.setOpeningTime(now.plus(1, ChronoUnit.HOURS));
        cp5.setClosingTime(now.plus(4, ChronoUnit.HOURS));
        cp5.setPoints(250);
        cp5.setLatitude(52.3791);
        cp5.setLongitude(4.9003);
        checkpoints.put(cp5.getId(), cp5);

        Log.info("🎭 MockCheckpointRepository initialized with " + checkpoints.size() + " checkpoints");
    }

    @Override
    public Optional<Checkpoint> findById(UUID id) {
        return Optional.ofNullable(checkpoints.get(id));
    }

    @Override
    public List<Checkpoint> findAll() {
        return new ArrayList<>(checkpoints.values());
    }

    @Override
    public List<Checkpoint> findByGameId(UUID gameId) {
        return checkpoints.values().stream()
                .filter(cp -> gameId.equals(cp.getGameId()))
                .sorted(Comparator.comparing(Checkpoint::getOpeningTime, Comparator.nullsLast(Comparator.naturalOrder())))
                .collect(Collectors.toList());
    }

    @Override
    public Optional<Checkpoint> findByGameIdAndExternalId(UUID gameId, String externalId) {
        return checkpoints.values().stream()
                .filter(cp -> gameId.equals(cp.getGameId()) && externalId.equals(cp.getExternalId()))
                .findFirst();
    }

    @Override
    public List<UUID> findContainingPoint(UUID gameId, Point point) {
        // In-memory equivalent of the JPA spatial query: haversine ≤ radius for circles,
        // JTS polygon containment for polygons. Keeps mock/test detection consistent with PostGIS.
        if (point == null) {
            return List.of();
        }
        return checkpoints.values().stream()
                .filter(cp -> gameId.equals(cp.getGameId()) && !cp.isHidden())
                .filter(cp -> zoneContains(cp, point))
                .map(Checkpoint::getId)
                .collect(Collectors.toList());
    }

    @Override
    public List<UUID> findCrossedBySegment(UUID gameId, Point a, Point b) {
        // In-memory equivalent of the JPA segment query: distance(center, segment) ≤ radius
        // for circles, JTS line/polygon intersection for polygons. A null endpoint degrades
        // to the point-containment check so callers never get worse results than before.
        if (a == null || b == null) {
            return findContainingPoint(gameId, b != null ? b : a);
        }
        return checkpoints.values().stream()
                .filter(cp -> gameId.equals(cp.getGameId()) && !cp.isHidden())
                .filter(cp -> zoneCrossedBySegment(cp, a, b))
                .map(Checkpoint::getId)
                .collect(Collectors.toList());
    }

    private static boolean zoneCrossedBySegment(Checkpoint cp, Point a, Point b) {
        return switch (cp) {
            case PolygonCheckpoint poly -> poly.getZonePolygon() != null
                    && poly.getZonePolygon().intersects(GEOMETRY_FACTORY.createLineString(
                            new Coordinate[]{a.getCoordinate(), b.getCoordinate()}));
            case CircleCheckpoint circle -> {
                if (cp.getLatitude() == null || cp.getLongitude() == null) {
                    yield false;
                }
                double radius = circle.getRadiusMeters() > 0 ? circle.getRadiusMeters() : 50.0;
                yield pointToSegmentMeters(cp.getLatitude(), cp.getLongitude(), a, b) <= radius;
            }
        };
    }

    /**
     * Distance in meters from a checkpoint centre to the segment a–b, using a local
     * equirectangular projection centred on the checkpoint. Planar geometry is accurate to
     * well under a meter at the sub-kilometre scales between consecutive fixes.
     */
    private static double pointToSegmentMeters(double centerLat, double centerLon, Point a, Point b) {
        double metersPerDegLat = 111_320.0;
        double metersPerDegLon = 111_320.0 * Math.cos(Math.toRadians(centerLat));
        // Project to local meters with the checkpoint centre at the origin.
        double px = 0.0, py = 0.0;
        double ax = (a.getX() - centerLon) * metersPerDegLon, ay = (a.getY() - centerLat) * metersPerDegLat;
        double bx = (b.getX() - centerLon) * metersPerDegLon, by = (b.getY() - centerLat) * metersPerDegLat;

        double dx = bx - ax, dy = by - ay;
        double lenSq = dx * dx + dy * dy;
        double t = lenSq == 0.0 ? 0.0 : ((px - ax) * dx + (py - ay) * dy) / lenSq;
        t = Math.max(0.0, Math.min(1.0, t));
        double cx = ax + t * dx, cy = ay + t * dy;
        return Math.hypot(px - cx, py - cy);
    }

    private static boolean zoneContains(Checkpoint cp, Point point) {
        return switch (cp) {
            case PolygonCheckpoint poly -> poly.getZonePolygon() != null && poly.getZonePolygon().contains(point);
            case CircleCheckpoint circle -> {
                if (cp.getLatitude() == null || cp.getLongitude() == null) {
                    yield false;
                }
                double radius = circle.getRadiusMeters() > 0 ? circle.getRadiusMeters() : 50.0;
                yield haversineMeters(point.getY(), point.getX(), cp.getLatitude(), cp.getLongitude()) <= radius;
            }
        };
    }

    private static double haversineMeters(double lat1, double lon1, double lat2, double lon2) {
        double earthRadiusMeters = 6_371_000.0;
        double dLat = Math.toRadians(lat2 - lat1);
        double dLon = Math.toRadians(lon2 - lon1);
        double a = Math.sin(dLat / 2) * Math.sin(dLat / 2)
                + Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2))
                * Math.sin(dLon / 2) * Math.sin(dLon / 2);
        return 2 * earthRadiusMeters * Math.asin(Math.min(1.0, Math.sqrt(a)));
    }

    @Override
    public Checkpoint save(Checkpoint checkpoint) {
        if (checkpoint.getId() == null) {
            checkpoint.setId(UUID.randomUUID());
        }
        checkpoint.setUpdatedAt(Instant.now());
        checkpoints.put(checkpoint.getId(), checkpoint);
        return checkpoint;
    }

    @Override
    public void delete(UUID id) {
        checkpoints.remove(id);
    }
}
