package nl.vlettenrally.data.mapper;

import nl.vlettenrally.data.domain.MessageTemplate;
import nl.vlettenrally.data.entity.jpa.MessageTemplateEntity;

/**
 * Mapper for converting between MessageTemplate domain objects and JPA entities.
 */
public class MessageTemplateMapper {

    private MessageTemplateMapper() {
        // Utility class
    }

    /**
     * Convert a JPA entity to a domain object.
     */
    public static MessageTemplate toDomain(MessageTemplateEntity entity) {
        if (entity == null) {
            return null;
        }

        MessageTemplate domain = new MessageTemplate();
        domain.setId(entity.getId());
        domain.setName(entity.getName());
        domain.setMessageType(entity.getMessageType());
        domain.setContent(entity.getContent());
        domain.setIcon(entity.getIcon());
        domain.setCreatedAt(entity.getCreatedAt());
        domain.setUpdatedAt(entity.getUpdatedAt());

        return domain;
    }

    /**
     * Convert a domain object to a JPA entity.
     */
    public static MessageTemplateEntity toEntity(MessageTemplate domain) {
        if (domain == null) {
            return null;
        }

        MessageTemplateEntity entity = new MessageTemplateEntity();
        entity.setId(domain.getId());
        entity.setName(domain.getName());
        entity.setMessageType(domain.getMessageType());
        entity.setContent(domain.getContent());
        entity.setIcon(domain.getIcon());
        entity.setCreatedAt(domain.getCreatedAt());
        entity.setUpdatedAt(domain.getUpdatedAt());

        return entity;
    }

    /**
     * Update an existing JPA entity with values from a domain object.
     */
    public static void updateEntity(MessageTemplateEntity entity, MessageTemplate domain) {
        if (entity == null || domain == null) {
            return;
        }

        entity.setName(domain.getName());
        entity.setMessageType(domain.getMessageType());
        entity.setContent(domain.getContent());
        entity.setIcon(domain.getIcon());
        entity.setUpdatedAt(domain.getUpdatedAt());
    }
}
