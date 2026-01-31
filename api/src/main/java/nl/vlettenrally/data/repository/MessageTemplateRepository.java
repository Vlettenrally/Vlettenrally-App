package nl.vlettenrally.data.repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import nl.vlettenrally.data.domain.MessageTemplate;

/**
 * Repository interface for MessageTemplate entities
 */
public interface MessageTemplateRepository {
    
    Optional<MessageTemplate> findById(UUID id);
    
    List<MessageTemplate> findAll();
    
    MessageTemplate save(MessageTemplate template);
    
    void delete(UUID id);
}
