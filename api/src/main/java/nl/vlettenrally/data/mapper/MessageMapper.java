package nl.vlettenrally.data.mapper;

import nl.vlettenrally.data.domain.Message;
import nl.vlettenrally.data.entity.jpa.MessageEntity;

import java.util.HashSet;

/**
 * Mapper for converting between Message domain objects and JPA entities.
 */
public class MessageMapper {

    private MessageMapper() {
        // Utility class
    }

    /**
     * Convert a JPA entity to a domain object.
     */
    public static Message toDomain(MessageEntity entity) {
        if (entity == null) {
            return null;
        }

        Message domain = new Message();
        domain.setId(entity.getId());
        domain.setGameId(entity.getGame() != null ? entity.getGame().getId() : null);
        domain.setTeamId(entity.getTeam() != null ? entity.getTeam().getId() : null);
        domain.setMessageType(entity.getMessageType());
        domain.setContent(entity.getContent());
        domain.setIcon(entity.getIcon());
        domain.setSentAt(entity.getSentAt());
        domain.setDelivered(entity.isDelivered());
        domain.setRead(entity.isRead());
        domain.setTransmittedVia(new HashSet<>(entity.getTransmittedVia()));
        domain.setTransmissionError(entity.getTransmissionError());
        domain.setSource(entity.getSource());
        domain.setSentByAdminId(entity.getSentByAdmin() != null ? entity.getSentByAdmin().getId() : null);
        domain.setBatchId(entity.getBatchId());

        return domain;
    }

    /**
     * Convert a domain object to a JPA entity.
     * Note: Relationships (game, team, sentByAdmin) must be set separately.
     */
    public static MessageEntity toEntity(Message domain) {
        if (domain == null) {
            return null;
        }

        MessageEntity entity = new MessageEntity();
        entity.setId(domain.getId());
        entity.setMessageType(domain.getMessageType());
        entity.setContent(domain.getContent());
        entity.setIcon(domain.getIcon());
        entity.setSentAt(domain.getSentAt());
        entity.setDelivered(domain.isDelivered());
        entity.setRead(domain.isRead());
        entity.setTransmittedVia(new HashSet<>(domain.getTransmittedVia()));
        entity.setTransmissionError(domain.getTransmissionError());
        entity.setSource(domain.getSource());
        entity.setBatchId(domain.getBatchId());

        return entity;
    }

    /**
     * Update an existing JPA entity with values from a domain object.
     */
    public static void updateEntity(MessageEntity entity, Message domain) {
        if (entity == null || domain == null) {
            return;
        }

        entity.setMessageType(domain.getMessageType());
        entity.setContent(domain.getContent());
        entity.setIcon(domain.getIcon());
        entity.setDelivered(domain.isDelivered());
        entity.setRead(domain.isRead());
        entity.setTransmittedVia(new HashSet<>(domain.getTransmittedVia()));
        entity.setTransmissionError(domain.getTransmissionError());
        entity.setSource(domain.getSource());
        entity.setBatchId(domain.getBatchId());
    }
}
