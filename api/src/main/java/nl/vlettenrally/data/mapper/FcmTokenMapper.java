package nl.vlettenrally.data.mapper;

import nl.vlettenrally.data.domain.FcmToken;
import nl.vlettenrally.data.entity.jpa.FcmTokenEntity;

/**
 * Mapper for converting between FcmToken domain objects and JPA entities.
 */
public class FcmTokenMapper {

    private FcmTokenMapper() {
        // Utility class
    }

    /**
     * Convert a JPA entity to a domain object.
     */
    public static FcmToken toDomain(FcmTokenEntity entity) {
        if (entity == null) {
            return null;
        }

        FcmToken domain = new FcmToken();
        domain.setId(entity.getId());
        domain.setDeviceRegistrationId(entity.getDeviceRegistration() != null ? entity.getDeviceRegistration().getId() : null);
        domain.setToken(entity.getToken());
        domain.setCreatedAt(entity.getCreatedAt());
        domain.setUpdatedAt(entity.getUpdatedAt());

        return domain;
    }

    /**
     * Convert a domain object to a JPA entity.
     * Note: DeviceRegistration relationship must be set separately.
     */
    public static FcmTokenEntity toEntity(FcmToken domain) {
        if (domain == null) {
            return null;
        }

        FcmTokenEntity entity = new FcmTokenEntity();
        entity.setId(domain.getId());
        entity.setToken(domain.getToken());
        entity.setCreatedAt(domain.getCreatedAt());
        entity.setUpdatedAt(domain.getUpdatedAt());

        return entity;
    }

    /**
     * Update an existing JPA entity with values from a domain object.
     */
    public static void updateEntity(FcmTokenEntity entity, FcmToken domain) {
        if (entity == null || domain == null) {
            return;
        }

        entity.setToken(domain.getToken());
        entity.setUpdatedAt(domain.getUpdatedAt());
    }
}
