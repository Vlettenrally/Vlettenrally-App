package nl.vlettenrally.data.repository.mock;

import io.quarkus.arc.profile.IfBuildProfile;
import io.quarkus.logging.Log;
import jakarta.annotation.PostConstruct;
import jakarta.enterprise.context.ApplicationScoped;
import nl.vlettenrally.data.domain.LocationData;
import nl.vlettenrally.data.repository.LocationDataRepository;
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
 *
 * Spatial distance calculations use the Haversine formula to approximate meter-based
 * distances, matching the PostGIS geography-cast behaviour in the JPA implementation.
 */
@IfBuildProfile(anyOf = {"dev", "test"})
@ApplicationScoped
public class MockLocationDataRepository implements LocationDataRepository {

    private static final GeometryFactory GEOMETRY_FACTORY = new GeometryFactory(new PrecisionModel(), 4326);

    /**
     * Mean Earth radius in meters (WGS-84).
     */
    private static final double EARTH_RADIUS_METERS = 6_371_008.8;

    private final Map<UUID, LocationData> locations = new ConcurrentHashMap<>();

    @PostConstruct
    void init() {
        initializeData();
    }

    public void initializeData() {
        locations.clear();

        Instant now = Instant.now();

        // Team Watervlooien – recent track near Amstel checkpoint (52.3580, 4.9000)
        addLocation(MockTeamRepository.TEAM_WATERVLOOIEN_ID, MockGameRepository.GAME_2026_ID,
                4.8995, 52.3582, now.minus(5, ChronoUnit.MINUTES),
                new BigDecimal("5.0"), null, new BigDecimal("8.5"), new BigDecimal("45.0"));
        addLocation(MockTeamRepository.TEAM_WATERVLOOIEN_ID, MockGameRepository.GAME_2026_ID,
                4.8998, 52.3579, now.minus(3, ChronoUnit.MINUTES),
                new BigDecimal("4.0"), null, new BigDecimal("7.2"), new BigDecimal("90.0"));
        addLocation(MockTeamRepository.TEAM_WATERVLOOIEN_ID, MockGameRepository.GAME_2026_ID,
                4.9001, 52.3580, now.minus(1, ChronoUnit.MINUTES),
                new BigDecimal("3.5"), null, new BigDecimal("6.0"), new BigDecimal("120.0"));

        // Team Windkracht – near Prinsengracht checkpoint (52.3738, 4.8820)
        addLocation(MockTeamRepository.TEAM_WINDKRACHT_ID, MockGameRepository.GAME_2026_ID,
                4.8825, 52.3740, now.minus(4, ChronoUnit.MINUTES),
                new BigDecimal("6.0"), null, new BigDecimal("9.0"), new BigDecimal("180.0"));
        addLocation(MockTeamRepository.TEAM_WINDKRACHT_ID, MockGameRepository.GAME_2026_ID,
                4.8822, 52.3737, now.minus(2, ChronoUnit.MINUTES),
                new BigDecimal("5.5"), null, new BigDecimal("8.0"), new BigDecimal("200.0"));

        // Team Gorillas – far from any checkpoint (north of Amsterdam)
        addLocation(MockTeamRepository.TEAM_GORILLAS_ID, MockGameRepository.GAME_2026_ID,
                4.9100, 52.3900, now.minus(6, ChronoUnit.MINUTES),
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
    public Optional<LocationData> findLatestByTeamId(UUID teamId) {
        return locations.values().stream()
                .filter(l -> teamId.equals(l.getTeamId()))
                .max(Comparator.comparing(LocationData::getLocationTime));
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
     * Haversine distance in meters between two JTS Points (longitude, latitude in degrees).
     * Approximates PostGIS ST_Distance(geography, geography) for short distances.
     */
    private static double haversineMeters(Point a, Point b) {
        double lat1 = Math.toRadians(a.getY());
        double lat2 = Math.toRadians(b.getY());
        double dLat = lat2 - lat1;
        double dLon = Math.toRadians(b.getX() - a.getX());

        double h = Math.sin(dLat / 2) * Math.sin(dLat / 2)
                 + Math.cos(lat1) * Math.cos(lat2)
                 * Math.sin(dLon / 2) * Math.sin(dLon / 2);

        return 2 * EARTH_RADIUS_METERS * Math.asin(Math.sqrt(h));
    }
}



