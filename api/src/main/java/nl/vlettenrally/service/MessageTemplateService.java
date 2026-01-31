package nl.vlettenrally.service;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import nl.vlettenrally.data.domain.MessageTemplate;
import nl.vlettenrally.data.enums.MessageType;

/**
 * Service interface for MessageTemplate operations
 */
public interface MessageTemplateService {
    
    Optional<MessageTemplate> findById(UUID id);
    
    List<MessageTemplate> findAll();
    
    MessageTemplate create(String name, MessageType messageType, String content, String icon);
    
    MessageTemplate update(UUID id, String name, MessageType messageType, String content, String icon);
    
    void delete(UUID id);
}
