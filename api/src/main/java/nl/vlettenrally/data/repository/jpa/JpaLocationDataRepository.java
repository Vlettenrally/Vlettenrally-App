package nl.vlettenrally.data.repository.jpa;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import nl.vlettenrally.data.domain.LocationData;
import nl.vlettenrally.data.entity.jpa.LocationDataEntity;
import nl.vlettenrally.data.mapper.LocationDataMapper;
import nl.vlettenrally.data.repository.LocationDataRepository;
import org.locationtech.jts.geom.Point;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * JPA implementation of LocationDataRepository.
 * Provides spatial queries using PostGIS for geofencing and route tracking.
 *
 * Note: For read operations, uses EntityManager directly since Panache
 * doesn't provide direct spatial query support.
 */
@ApplicationScoped
public class JpaLocationDataRepository implements LocationDataRepository {

    @Inject
    EntityManager em;

    @Override
    public Optional<LocationData> findById(UUID id) {
        LocationDataEntity entity = em.find(LocationDataEntity.class, id);
        return Optional.ofNullable(LocationDataMapper.toDomain(entity));
    }

    @Override
    public List<LocationData> findByTeamId(UUID teamId) {
        List<LocationDataEntity> entities = em.createQuery(
                "SELECT l FROM LocationDataEntity l WHERE l.team.id = :teamId ORDER BY l.locationTime DESC",
                LocationDataEntity.class)
                .setParameter("teamId", teamId)
                .getResultList();
        return entities.stream().map(LocationDataMapper::toDomain).collect(Collectors.toList());
    }

    @Override
    public List<LocationData> findByTeamIdAndTimeRange(UUID teamId, Instant startTime, Instant endTime) {
        List<LocationDataEntity> entities = em.createQuery(
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
        List<LocationDataEntity> entities = em.createQuery(
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
        List<LocationDataEntity> entities = em.createQuery(
                "SELECT l FROM LocationDataEntity l WHERE l.game.id = :gameId ORDER BY l.locationTime DESC",
                LocationDataEntity.class)
                .setParameter("gameId", gameId)
                .getResultList();
        return entities.stream().map(LocationDataMapper::toDomain).collect(Collectors.toList());
    }

    @Override
    public List<LocationData> findByGameIdAndTimeRange(UUID gameId, Instant startTime, Instant endTime) {
        List<LocationDataEntity> entities = em.createQuery(
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
        // Using PostGIS ST_DWithin for distance-based proximity search
        // ST_DWithin(geometry1, geometry2, distance) - checks if geometries are within distance
        List<LocationDataEntity> entities = em.createNativeQuery(
                "SELECT l.id, l.team_id, l.game_id, l.location, l.location_time, " +
                "l.accuracy, l.altitude, l.speed, l.heading, l.created_at " +
                "FROM location_data l " +
                "WHERE ST_DWithin(l.location, ?1::geometry, ?2) " +
                "ORDER BY l.location_time DESC",
                LocationDataEntity.class)
                .setParameter(1, point)
                .setParameter(2, distanceMeters)
                .getResultList();
        return entities.stream().map(LocationDataMapper::toDomain).collect(Collectors.toList());
    }

    @Override
    public List<LocationData> findTeamLocationsWithinDistance(UUID teamId, Point point, double distanceMeters) {
        List<LocationDataEntity> entities = em.createNativeQuery(
                "SELECT l.id, l.team_id, l.game_id, l.location, l.location_time, " +
                "l.accuracy, l.altitude, l.speed, l.heading, l.created_at " +
                "FROM location_data l " +
                "WHERE l.team_id = ?1 AND ST_DWithin(l.location, ?2::geometry, ?3) " +
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
        List<LocationDataEntity> entities = em.createNativeQuery(
                "SELECT l.id, l.team_id, l.game_id, l.location, l.location_time, " +
                "l.accuracy, l.altitude, l.speed, l.heading, l.created_at " +
                "FROM location_data l " +
                "WHERE l.team_id = ?1 AND ST_DWithin(l.location, ?2::geometry, ?3) " +
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
    public LocationData save(LocationData location) {
        LocationDataEntity entity = LocationDataMapper.toEntity(location);

        // Retrieve and set relationships
        if (location.getTeamId() != null) {
            entity.setTeam(em.getReference(nl.vlettenrally.data.entity.jpa.TeamEntity.class, location.getTeamId()));
        }
        if (location.getGameId() != null) {
            entity.setGame(em.getReference(nl.vlettenrally.data.entity.jpa.GameEntity.class, location.getGameId()));
        }

        em.persist(entity);
        return LocationDataMapper.toDomain(entity);
    }

    @Override
    public void delete(UUID id) {
        LocationDataEntity entity = em.find(LocationDataEntity.class, id);
        if (entity != null) {
            em.remove(entity);
        }
    }

    @Override
    public void deleteByTeamId(UUID teamId) {
        em.createQuery("DELETE FROM LocationDataEntity l WHERE l.team.id = :teamId")
                .setParameter("teamId", teamId)
                .executeUpdate();
    }
}

