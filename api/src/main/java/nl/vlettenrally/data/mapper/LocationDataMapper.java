package nl.vlettenrally.data.mapper;

import nl.vlettenrally.data.domain.LocationData;
import nl.vlettenrally.data.entity.jpa.LocationDataEntity;

/**
 * Mapper for converting between LocationData domain objects and JPA entities.
 */
public class LocationDataMapper {

    private LocationDataMapper() {
        // Utility class
    }

    /**
     * Convert a JPA entity to a domain object.
     */
    public static LocationData toDomain(LocationDataEntity entity) {
        if (entity == null) {
            return null;
        }

        LocationData domain = new LocationData();
        domain.setId(entity.getId());
        domain.setTeamId(entity.getTeam() != null ? entity.getTeam().getId() : null);
        domain.setGameId(entity.getGame() != null ? entity.getGame().getId() : null);
        domain.setLocation(entity.getLocation());
        domain.setLocationTime(entity.getLocationTime());
        domain.setAccuracy(entity.getAccuracy());
        domain.setAltitude(entity.getAltitude());
        domain.setSpeed(entity.getSpeed());
        domain.setHeading(entity.getHeading());
        domain.setCreatedAt(entity.getCreatedAt());

        return domain;
    }

    /**
     * Convert a domain object to a JPA entity.
     * Note: Team and Game relationships must be set separately.
     */
    public static LocationDataEntity toEntity(LocationData domain) {
        if (domain == null) {
            return null;
        }

        LocationDataEntity entity = new LocationDataEntity();
        if (domain.getId() != null) {
            entity.setId(domain.getId());
        }
        entity.setLocation(domain.getLocation());
        entity.setLocationTime(domain.getLocationTime());
        entity.setAccuracy(domain.getAccuracy());
        entity.setAltitude(domain.getAltitude());
        entity.setSpeed(domain.getSpeed());
        entity.setHeading(domain.getHeading());
        if (domain.getCreatedAt() != null) {
            entity.setCreatedAt(domain.getCreatedAt());
        }

        return entity;
    }

    /**
     * Update an existing JPA entity with values from a domain object.
     */
    public static void updateEntity(LocationDataEntity entity, LocationData domain) {
        if (entity == null || domain == null) {
            return;
        }

        entity.setLocation(domain.getLocation());
        entity.setLocationTime(domain.getLocationTime());
        entity.setAccuracy(domain.getAccuracy());
        entity.setAltitude(domain.getAltitude());
        entity.setSpeed(domain.getSpeed());
        entity.setHeading(domain.getHeading());
    }
}

