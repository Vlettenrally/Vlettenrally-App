package nl.vlettenrally.data.mapper;

import nl.vlettenrally.data.domain.WebSocketRegistration;
import nl.vlettenrally.data.entity.jpa.WebSocketRegistrationEntity;

/**
 * Mapper for converting between WebSocketRegistration domain objects and JPA entities.
 */
public class WebSocketRegistrationMapper {

    private WebSocketRegistrationMapper() {
        // Utility class
    }

    /**
     * Convert a JPA entity to a domain object.
     */
    public static WebSocketRegistration toDomain(WebSocketRegistrationEntity entity) {
        if (entity == null) {
            return null;
        }

        WebSocketRegistration domain = new WebSocketRegistration();
        domain.setId(entity.getId());
        domain.setDeviceRegistrationId(entity.getDeviceRegistration() != null ? entity.getDeviceRegistration().getId() : null);
        domain.setSessionId(entity.getSessionId());
        domain.setConnectedAt(entity.getConnectedAt());
        domain.setDisconnectedAt(entity.getDisconnectedAt());

        return domain;
    }

    /**
     * Convert a domain object to a JPA entity.
     * Note: DeviceRegistration relationship must be set separately.
     */
    public static WebSocketRegistrationEntity toEntity(WebSocketRegistration domain) {
        if (domain == null) {
            return null;
        }

        WebSocketRegistrationEntity entity = new WebSocketRegistrationEntity();
        entity.setId(domain.getId());
        entity.setSessionId(domain.getSessionId());
        entity.setConnectedAt(domain.getConnectedAt());
        entity.setDisconnectedAt(domain.getDisconnectedAt());

        return entity;
    }

    /**
     * Update an existing JPA entity with values from a domain object.
     */
    public static void updateEntity(WebSocketRegistrationEntity entity, WebSocketRegistration domain) {
        if (entity == null || domain == null) {
            return;
        }

        entity.setSessionId(domain.getSessionId());
        entity.setDisconnectedAt(domain.getDisconnectedAt());
    }
}
