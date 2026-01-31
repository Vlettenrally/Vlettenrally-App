package nl.vlettenrally.data.mapper;

import nl.vlettenrally.data.domain.MessageDelivery;
import nl.vlettenrally.data.entity.jpa.MessageDeliveryEntity;

import java.util.HashSet;

/**
 * Mapper for converting between MessageDelivery domain objects and JPA entities.
 */
public class MessageDeliveryMapper {

    private MessageDeliveryMapper() {
        // Utility class
    }

    /**
     * Convert a JPA entity to a domain object.
     */
    public static MessageDelivery toDomain(MessageDeliveryEntity entity) {
        if (entity == null) {
            return null;
        }

        MessageDelivery domain = new MessageDelivery();
        domain.setId(entity.getId());
        domain.setMessageId(entity.getMessage() != null ? entity.getMessage().getId() : null);
        domain.setTeamId(entity.getTeam() != null ? entity.getTeam().getId() : null);
        domain.setTransmittedVia(new HashSet<>(entity.getTransmittedVia()));
        domain.setDelivered(entity.isDelivered());
        domain.setTransmissionError(entity.getTransmissionError());
        domain.setCreatedAt(entity.getCreatedAt());
        domain.setUpdatedAt(entity.getUpdatedAt());

        return domain;
    }

    /**
     * Convert a domain object to a JPA entity.
     * Note: Relationships (message, team) must be set separately.
     */
    public static MessageDeliveryEntity toEntity(MessageDelivery domain) {
        if (domain == null) {
            return null;
        }

        MessageDeliveryEntity entity = new MessageDeliveryEntity();
        entity.setId(domain.getId());
        entity.setTransmittedVia(new HashSet<>(domain.getTransmittedVia()));
        entity.setDelivered(domain.isDelivered());
        entity.setTransmissionError(domain.getTransmissionError());
        entity.setCreatedAt(domain.getCreatedAt());
        entity.setUpdatedAt(domain.getUpdatedAt());

        return entity;
    }

    /**
     * Update an existing JPA entity with values from a domain object.
     */
    public static void updateEntity(MessageDeliveryEntity entity, MessageDelivery domain) {
        if (entity == null || domain == null) {
            return;
        }

        entity.setTransmittedVia(new HashSet<>(domain.getTransmittedVia()));
        entity.setDelivered(domain.isDelivered());
        entity.setTransmissionError(domain.getTransmissionError());
        entity.setUpdatedAt(domain.getUpdatedAt());
    }
}
