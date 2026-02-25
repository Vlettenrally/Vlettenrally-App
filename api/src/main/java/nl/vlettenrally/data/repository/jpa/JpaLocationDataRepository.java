package nl.vlettenrally.data.repository.jpa;

import io.quarkus.arc.profile.IfBuildProfile;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.transaction.Transactional;
import nl.vlettenrally.data.domain.LocationData;
import nl.vlettenrally.data.entity.jpa.GameEntity;
import nl.vlettenrally.data.entity.jpa.LocationDataEntity;
import nl.vlettenrally.data.entity.jpa.TeamEntity;
import nl.vlettenrally.data.mapper.LocationDataMapper;
import nl.vlettenrally.data.repository.LocationDataRepository;
import org.locationtech.jts.geom.Point;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * JPA implementation of LocationDataRepository with read replica support.
 * Only active in prod profile - replaced by MockLocationDataRepository in dev/test.
 *
 * Provides spatial queries using PostGIS for geofencing and route tracking.
 * All spatial distance queries cast to ::geography so distances are in meters.
 */
@IfBuildProfile("prod")
@ApplicationScoped
public class JpaLocationDataRepository extends ReadReplicaAwareRepository implements LocationDataRepository {

    @Override
    public Optional<LocationData> findById(UUID id) {
        List<LocationDataEntity> entities = getReadEntityManager().createQuery(
                "SELECT l FROM LocationDataEntity l WHERE l.id = :id ORDER BY l.locationTime DESC",
                LocationDataEntity.class)
                .setParameter("id", id)
                .setMaxResults(1)
                .getResultList();
        return entities.stream().findFirst().map(LocationDataMapper::toDomain);
    }

    @Override
    public List<LocationData> findByTeamId(UUID teamId) {
        List<LocationDataEntity> entities = getReadEntityManager().createQuery(
                "SELECT l FROM LocationDataEntity l WHERE l.team.id = :teamId ORDER BY l.locationTime DESC",
                LocationDataEntity.class)
                .setParameter("teamId", teamId)
                .getResultList();
        return entities.stream().map(LocationDataMapper::toDomain).collect(Collectors.toList());
    }

    @Override
    public List<LocationData> findByTeamIdAndTimeRange(UUID teamId, Instant startTime, Instant endTime) {
        List<LocationDataEntity> entities = getReadEntityManager().createQuery(
                "SELECT l FROM LocationDataEntity l WHERE l.team.id = :teamId " +
                "AND l.locationTime >= :startTime AND l.locationTime <= :endTime " +
                "ORDER BY l.locationTime DESC",
                LocationDataEntity.class)
                .setParameter("teamId", teamId)
                .setParameter("startTime", startTime)
                .setParameter("endTime", endTime)
                .getResultList();
        return entities.stream().map(LocationDataMapper::toDomain).collect(Collectors.toList());
    }

    @Override
    public Optional<LocationData> findLatestByTeamId(UUID teamId) {
        List<LocationDataEntity> entities = getReadEntityManager().createQuery(
                "SELECT l FROM LocationDataEntity l WHERE l.team.id = :teamId " +
                "ORDER BY l.locationTime DESC",
                LocationDataEntity.class)
                .setParameter("teamId", teamId)
                .setMaxResults(1)
                .getResultList();
        return entities.stream().findFirst().map(LocationDataMapper::toDomain);
    }

    @Override
    public List<LocationData> findByGameId(UUID gameId) {
        List<LocationDataEntity> entities = getReadEntityManager().createQuery(
                "SELECT l FROM LocationDataEntity l WHERE l.game.id = :gameId ORDER BY l.locationTime DESC",
                LocationDataEntity.class)
                .setParameter("gameId", gameId)
                .getResultList();
        return entities.stream().map(LocationDataMapper::toDomain).collect(Collectors.toList());
    }

    @Override
    public List<LocationData> findByGameIdAndTimeRange(UUID gameId, Instant startTime, Instant endTime) {
        List<LocationDataEntity> entities = getReadEntityManager().createQuery(
                "SELECT l FROM LocationDataEntity l WHERE l.game.id = :gameId " +
                "AND l.locationTime >= :startTime AND l.locationTime <= :endTime " +
                "ORDER BY l.locationTime DESC",
                LocationDataEntity.class)
                .setParameter("gameId", gameId)
                .setParameter("startTime", startTime)
                .setParameter("endTime", endTime)
                .getResultList();
        return entities.stream().map(LocationDataMapper::toDomain).collect(Collectors.toList());
    }

    @Override
    public List<LocationData> findWithinDistance(Point point, double distanceMeters) {
        // Using PostGIS ST_DWithin with geography cast for distance-based proximity search
        // Casting to ::geography ensures the distance parameter is interpreted as meters (not degrees)
        List<LocationDataEntity> entities = getReadEntityManager().createNativeQuery(
                "SELECT l.id, l.team_id, l.game_id, l.location, l.location_time, " +
                "l.accuracy, l.altitude, l.speed, l.heading, l.created_at " +
                "FROM location_data l " +
                "WHERE ST_DWithin(l.location::geography, ?1::geography, ?2) " +
                "ORDER BY l.location_time DESC",
                LocationDataEntity.class)
                .setParameter(1, point)
                .setParameter(2, distanceMeters)
                .getResultList();
        return entities.stream().map(LocationDataMapper::toDomain).collect(Collectors.toList());
    }

    @Override
    public List<LocationData> findTeamLocationsWithinDistance(UUID teamId, Point point, double distanceMeters) {
        List<LocationDataEntity> entities = getReadEntityManager().createNativeQuery(
                "SELECT l.id, l.team_id, l.game_id, l.location, l.location_time, " +
                "l.accuracy, l.altitude, l.speed, l.heading, l.created_at " +
                "FROM location_data l " +
                "WHERE l.team_id = ?1 AND ST_DWithin(l.location::geography, ?2::geography, ?3) " +
                "ORDER BY l.location_time DESC",
                LocationDataEntity.class)
                .setParameter(1, teamId)
                .setParameter(2, point)
                .setParameter(3, distanceMeters)
                .getResultList();
        return entities.stream().map(LocationDataMapper::toDomain).collect(Collectors.toList());
    }

    @Override
    public List<LocationData> findTeamLocationsWithinDistanceAndTime(UUID teamId, Point point,
                                                                      double distanceMeters, Instant startTime, Instant endTime) {
        List<LocationDataEntity> entities = getReadEntityManager().createNativeQuery(
                "SELECT l.id, l.team_id, l.game_id, l.location, l.location_time, " +
                "l.accuracy, l.altitude, l.speed, l.heading, l.created_at " +
                "FROM location_data l " +
                "WHERE l.team_id = ?1 AND ST_DWithin(l.location::geography, ?2::geography, ?3) " +
                "AND l.location_time >= ?4 AND l.location_time <= ?5 " +
                "ORDER BY l.location_time DESC",
                LocationDataEntity.class)
                .setParameter(1, teamId)
                .setParameter(2, point)
                .setParameter(3, distanceMeters)
                .setParameter(4, startTime)
                .setParameter(5, endTime)
                .getResultList();
        return entities.stream().map(LocationDataMapper::toDomain).collect(Collectors.toList());
    }

    @Override
    public boolean isWithinDistance(Point point1, Point point2, double distanceMeters) {
        // Delegate geodetic distance check to PostGIS using geography cast for accurate meter-based results
        Boolean result = (Boolean) getReadEntityManager().createNativeQuery(
                "SELECT ST_DWithin(?1::geography, ?2::geography, ?3)")
                .setParameter(1, point1)
                .setParameter(2, point2)
                .setParameter(3, distanceMeters)
                .getSingleResult();
        return Boolean.TRUE.equals(result);
    }

    @Transactional
    @Override
    public LocationData save(LocationData location) {
        LocationDataEntity entity = LocationDataMapper.toEntity(location);

        // Retrieve and set relationships
        if (location.getTeamId() != null) {
            entity.setTeam(getWriteEntityManager().getReference(TeamEntity.class, location.getTeamId()));
        }
        if (location.getGameId() != null) {
            entity.setGame(getWriteEntityManager().getReference(GameEntity.class, location.getGameId()));
        }

        getWriteEntityManager().persist(entity);
        return LocationDataMapper.toDomain(entity);
    }

    @Transactional
    @Override
    public void delete(UUID id) {
        getWriteEntityManager().createQuery("DELETE FROM LocationDataEntity l WHERE l.id = :id")
                .setParameter("id", id)
                .executeUpdate();
    }

    @Transactional
    @Override
    public void deleteByTeamId(UUID teamId) {
        getWriteEntityManager().createQuery("DELETE FROM LocationDataEntity l WHERE l.team.id = :teamId")
                .setParameter("teamId", teamId)
                .executeUpdate();
    }
}

