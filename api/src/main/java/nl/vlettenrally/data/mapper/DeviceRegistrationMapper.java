package nl.vlettenrally.data.mapper;

import nl.vlettenrally.data.domain.DeviceRegistration;
import nl.vlettenrally.data.entity.jpa.DeviceRegistrationEntity;

/**
 * Mapper for converting between DeviceRegistration domain objects and JPA entities.
 */
public class DeviceRegistrationMapper {

    private DeviceRegistrationMapper() {
        // Utility class
    }

    /**
     * Convert a JPA entity to a domain object.
     */
    public static DeviceRegistration toDomain(DeviceRegistrationEntity entity) {
        if (entity == null) {
            return null;
        }

        DeviceRegistration domain = new DeviceRegistration();
        domain.setId(entity.getId());
        domain.setTeamId(entity.getTeam() != null ? entity.getTeam().getId() : null);
        domain.setAdminId(entity.getAdmin() != null ? entity.getAdmin().getId() : null);
        domain.setGameId(entity.getGameId());
        domain.setNickname(entity.getNickname());
        domain.setLastSeen(entity.getLastSeen());
        domain.setCreatedAt(entity.getCreatedAt());

        return domain;
    }

    /**
     * Convert a domain object to a JPA entity.
     * Note: Relationships (team, admin) must be set separately.
     */
    public static DeviceRegistrationEntity toEntity(DeviceRegistration domain) {
        if (domain == null) {
            return null;
        }

        DeviceRegistrationEntity entity = new DeviceRegistrationEntity();
        entity.setId(domain.getId());
        entity.setGameId(domain.getGameId());
        entity.setNickname(domain.getNickname());
        entity.setLastSeen(domain.getLastSeen());
        entity.setCreatedAt(domain.getCreatedAt());

        return entity;
    }

    /**
     * Update an existing JPA entity with values from a domain object.
     */
    public static void updateEntity(DeviceRegistrationEntity entity, DeviceRegistration domain) {
        if (entity == null || domain == null) {
            return;
        }

        entity.setGameId(domain.getGameId());
        entity.setNickname(domain.getNickname());
        entity.setLastSeen(domain.getLastSeen());
    }
}
