package nl.vlettenrally.data.repository.mock;

import io.quarkus.arc.profile.IfBuildProfile;
import io.quarkus.logging.Log;
import jakarta.annotation.PostConstruct;
import jakarta.enterprise.context.ApplicationScoped;
import nl.vlettenrally.data.domain.LocationData;
import nl.vlettenrally.data.repository.LocationDataRepository;
import nl.vlettenrally.service.util.GeoDistance;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.Point;
import org.locationtech.jts.geom.PrecisionModel;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * Mock implementation of LocationDataRepository with prefilled data.
 * Only active in dev and test profiles - replaced by JpaLocationDataRepository in prod.
 * <p>
 * Spatial distance calculations use the Haversine formula to approximate meter-based
 * distances, matching the PostGIS geography-cast behaviour in the JPA implementation.
 */
@IfBuildProfile(anyOf = {"dev", "test"})
@ApplicationScoped
public class MockLocationDataRepository implements LocationDataRepository {

    private static final GeometryFactory GEOMETRY_FACTORY = new GeometryFactory(new PrecisionModel(), 4326);

    private final Map<UUID, LocationData> locations = new ConcurrentHashMap<>();

    @PostConstruct
    void init() {
        initializeData();
    }

    public void initializeData() {
        locations.clear();

        Instant now = Instant.now();
        UUID watervlooien = MockTeamRepository.TEAM_WATERVLOOIEN_ID;
        UUID windkracht = MockTeamRepository.TEAM_WINDKRACHT_ID;
        UUID gorillas = MockTeamRepository.TEAM_GORILLAS_ID;
        UUID game = MockGameRepository.GAME_2026_ID;

        // Team Watervlooien – approaching the Amstel checkpoint (52.3580, 4.9000)
        // from the southwest. Latest point at t-1min is roughly on the checkpoint.
        addLocation(watervlooien, game, 4.8930, 52.3520, now.minus(60, ChronoUnit.MINUTES),
                new BigDecimal("7.0"), null, new BigDecimal("9.5"), new BigDecimal("40.0"));
        addLocation(watervlooien, game, 4.8945, 52.3535, now.minus(50, ChronoUnit.MINUTES),
                new BigDecimal("6.5"), null, new BigDecimal("9.0"), new BigDecimal("42.0"));
        addLocation(watervlooien, game, 4.8960, 52.3548, now.minus(40, ChronoUnit.MINUTES),
                new BigDecimal("6.0"), null, new BigDecimal("8.8"), new BigDecimal("44.0"));
        addLocation(watervlooien, game, 4.8970, 52.3560, now.minus(30, ChronoUnit.MINUTES),
                new BigDecimal("5.5"), null, new BigDecimal("8.5"), new BigDecimal("45.0"));
        addLocation(watervlooien, game, 4.8978, 52.3568, now.minus(22, ChronoUnit.MINUTES),
                new BigDecimal("5.0"), null, new BigDecimal("8.2"), new BigDecimal("45.0"));
        addLocation(watervlooien, game, 4.8985, 52.3574, now.minus(15, ChronoUnit.MINUTES),
                new BigDecimal("5.0"), null, new BigDecimal("8.0"), new BigDecimal("48.0"));
        addLocation(watervlooien, game, 4.8990, 52.3578, now.minus(10, ChronoUnit.MINUTES),
                new BigDecimal("5.0"), null, new BigDecimal("8.5"), new BigDecimal("50.0"));
        addLocation(watervlooien, game, 4.8995, 52.3582, now.minus(5, ChronoUnit.MINUTES),
                new BigDecimal("5.0"), null, new BigDecimal("8.5"), new BigDecimal("45.0"));
        addLocation(watervlooien, game, 4.8998, 52.3579, now.minus(3, ChronoUnit.MINUTES),
                new BigDecimal("4.0"), null, new BigDecimal("7.2"), new BigDecimal("90.0"));
        addLocation(watervlooien, game, 4.9001, 52.3580, now.minus(1, ChronoUnit.MINUTES),
                new BigDecimal("3.5"), null, new BigDecimal("6.0"), new BigDecimal("120.0"));

        // Team Windkracht – approaching Prinsengracht checkpoint (52.3738, 4.8820) from the southeast.
        addLocation(windkracht, game, 4.8880, 52.3690, now.minus(55, ChronoUnit.MINUTES),
                new BigDecimal("7.0"), null, new BigDecimal("9.5"), new BigDecimal("310.0"));
        addLocation(windkracht, game, 4.8865, 52.3700, now.minus(45, ChronoUnit.MINUTES),
                new BigDecimal("6.5"), null, new BigDecimal("9.2"), new BigDecimal("315.0"));
        addLocation(windkracht, game, 4.8850, 52.3710, now.minus(35, ChronoUnit.MINUTES),
                new BigDecimal("6.0"), null, new BigDecimal("9.0"), new BigDecimal("320.0"));
        addLocation(windkracht, game, 4.8840, 52.3720, now.minus(25, ChronoUnit.MINUTES),
                new BigDecimal("5.5"), null, new BigDecimal("8.8"), new BigDecimal("330.0"));
        addLocation(windkracht, game, 4.8832, 52.3728, now.minus(15, ChronoUnit.MINUTES),
                new BigDecimal("5.5"), null, new BigDecimal("8.5"), new BigDecimal("340.0"));
        addLocation(windkracht, game, 4.8827, 52.3733, now.minus(9, ChronoUnit.MINUTES),
                new BigDecimal("6.0"), null, new BigDecimal("8.2"), new BigDecimal("350.0"));
        addLocation(windkracht, game, 4.8825, 52.3740, now.minus(4, ChronoUnit.MINUTES),
                new BigDecimal("6.0"), null, new BigDecimal("9.0"), new BigDecimal("180.0"));
        addLocation(windkracht, game, 4.8822, 52.3737, now.minus(2, ChronoUnit.MINUTES),
                new BigDecimal("5.5"), null, new BigDecimal("8.0"), new BigDecimal("200.0"));

        // Team Gorillas – drifting north of Amsterdam in a rough loop.
        addLocation(gorillas, game, 4.9120, 52.3870, now.minus(55, ChronoUnit.MINUTES),
                new BigDecimal("8.0"), null, new BigDecimal("7.5"), new BigDecimal("30.0"));
        addLocation(gorillas, game, 4.9140, 52.3885, now.minus(45, ChronoUnit.MINUTES),
                new BigDecimal("8.0"), null, new BigDecimal("8.0"), new BigDecimal("60.0"));
        addLocation(gorillas, game, 4.9150, 52.3895, now.minus(35, ChronoUnit.MINUTES),
                new BigDecimal("8.5"), null, new BigDecimal("8.5"), new BigDecimal("90.0"));
        addLocation(gorillas, game, 4.9145, 52.3905, now.minus(25, ChronoUnit.MINUTES),
                new BigDecimal("8.0"), null, new BigDecimal("9.0"), new BigDecimal("180.0"));
        addLocation(gorillas, game, 4.9130, 52.3910, now.minus(18, ChronoUnit.MINUTES),
                new BigDecimal("8.0"), null, new BigDecimal("9.5"), new BigDecimal("220.0"));
        addLocation(gorillas, game, 4.9115, 52.3908, now.minus(12, ChronoUnit.MINUTES),
                new BigDecimal("8.0"), null, new BigDecimal("10.0"), new BigDecimal("260.0"));
        addLocation(gorillas, game, 4.9105, 52.3903, now.minus(9, ChronoUnit.MINUTES),
                new BigDecimal("8.0"), null, new BigDecimal("11.0"), new BigDecimal("350.0"));
        addLocation(gorillas, game, 4.9100, 52.3900, now.minus(6, ChronoUnit.MINUTES),
                new BigDecimal("8.0"), null, new BigDecimal("12.0"), new BigDecimal("0.0"));

        Log.info("🎭 MockLocationDataRepository initialized with " + locations.size() + " location points");
    }

    private void addLocation(UUID teamId, UUID gameId, double longitude, double latitude,
                             Instant locationTime, BigDecimal accuracy, BigDecimal altitude,
                             BigDecimal speed, BigDecimal heading) {
        LocationData loc = new LocationData();
        loc.setTeamId(teamId);
        loc.setGameId(gameId);
        loc.setLocation(GEOMETRY_FACTORY.createPoint(new Coordinate(longitude, latitude)));
        loc.setLocationTime(locationTime);
        loc.setAccuracy(accuracy);
        loc.setAltitude(altitude);
        loc.setSpeed(speed);
        loc.setHeading(heading);
        locations.put(loc.getId(), loc);
    }

    @Override
    public Optional<LocationData> findById(UUID id) {
        return Optional.ofNullable(locations.get(id));
    }

    @Override
    public List<LocationData> findByTeamId(UUID teamId) {
        return locations.values().stream()
                .filter(l -> teamId.equals(l.getTeamId()))
                .sorted(Comparator.comparing(LocationData::getLocationTime).reversed())
                .collect(Collectors.toList());
    }

    @Override
    public List<LocationData> findByTeamIdAndTimeRange(UUID teamId, Instant startTime, Instant endTime) {
        return locations.values().stream()
                .filter(l -> teamId.equals(l.getTeamId()))
                .filter(l -> !l.getLocationTime().isBefore(startTime) && !l.getLocationTime().isAfter(endTime))
                .sorted(Comparator.comparing(LocationData::getLocationTime).reversed())
                .collect(Collectors.toList());
    }

    @Override
    public List<LocationData> findByTeamIdAfter(UUID teamId, Instant afterExclusive) {
        return locations.values().stream()
                .filter(l -> teamId.equals(l.getTeamId()))
                .filter(l -> l.getLocationTime().isAfter(afterExclusive))
                .sorted(Comparator.comparing(LocationData::getLocationTime))
                .collect(Collectors.toList());
    }

    @Override
    public Optional<LocationData> findLatestByTeamId(UUID teamId) {
        return locations.values().stream()
                .filter(l -> teamId.equals(l.getTeamId()))
                .max(Comparator.comparing(LocationData::getLocationTime));
    }

    @Override
    public List<LocationData> findRecentByTeamId(UUID teamId, int limit) {
        return locations.values().stream()
                .filter(l -> teamId.equals(l.getTeamId()))
                .sorted(Comparator.comparing(LocationData::getLocationTime).reversed())
                .limit(limit)
                .collect(Collectors.toList());
    }

    @Override
    public List<LocationData> findByGameId(UUID gameId) {
        return locations.values().stream()
                .filter(l -> gameId.equals(l.getGameId()))
                .sorted(Comparator.comparing(LocationData::getLocationTime).reversed())
                .collect(Collectors.toList());
    }

    @Override
    public List<LocationData> findByGameIdAndTimeRange(UUID gameId, Instant startTime, Instant endTime) {
        return locations.values().stream()
                .filter(l -> gameId.equals(l.getGameId()))
                .filter(l -> !l.getLocationTime().isBefore(startTime) && !l.getLocationTime().isAfter(endTime))
                .sorted(Comparator.comparing(LocationData::getLocationTime).reversed())
                .collect(Collectors.toList());
    }

    @Override
    public List<LocationData> findWithinDistance(Point point, double distanceMeters) {
        return locations.values().stream()
                .filter(l -> haversineMeters(l.getLocation(), point) <= distanceMeters)
                .sorted(Comparator.comparing(LocationData::getLocationTime).reversed())
                .collect(Collectors.toList());
    }

    @Override
    public List<LocationData> findTeamLocationsWithinDistance(UUID teamId, Point point, double distanceMeters) {
        return locations.values().stream()
                .filter(l -> teamId.equals(l.getTeamId()))
                .filter(l -> haversineMeters(l.getLocation(), point) <= distanceMeters)
                .sorted(Comparator.comparing(LocationData::getLocationTime).reversed())
                .collect(Collectors.toList());
    }

    @Override
    public List<LocationData> findTeamLocationsWithinDistanceAndTime(UUID teamId, Point point,
                                                                      double distanceMeters, Instant startTime, Instant endTime) {
        return locations.values().stream()
                .filter(l -> teamId.equals(l.getTeamId()))
                .filter(l -> haversineMeters(l.getLocation(), point) <= distanceMeters)
                .filter(l -> !l.getLocationTime().isBefore(startTime) && !l.getLocationTime().isAfter(endTime))
                .sorted(Comparator.comparing(LocationData::getLocationTime).reversed())
                .collect(Collectors.toList());
    }

    @Override
    public boolean isWithinDistance(Point point1, Point point2, double distanceMeters) {
        return haversineMeters(point1, point2) <= distanceMeters;
    }

    @Override
    public LocationData save(LocationData location) {
        if (location.getId() == null) {
            location.setId(UUID.randomUUID());
        }
        locations.put(location.getId(), location);
        return location;
    }

    @Override
    public void delete(UUID id) {
        locations.remove(id);
    }

    @Override
    public void deleteByTeamId(UUID teamId) {
        locations.entrySet().removeIf(entry -> teamId.equals(entry.getValue().getTeamId()));
    }

    /**
     * Haversine distance in meters between two JTS Points, matching the PostGIS
     * geography-cast behaviour in the JPA implementation.
     */
    private static double haversineMeters(Point a, Point b) {
        return GeoDistance.haversineMeters(a, b);
    }
}



