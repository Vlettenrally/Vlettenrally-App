package nl.vlettenrally.repository;

import nl.vlettenrally.domain.Message;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Repository interface for Message entities
 */
public interface MessageRepository {
    
    Optional<Message> findById(UUID id);
    
    List<Message> findAll();
    
    List<Message> findByTeamId(UUID teamId);
    
    List<Message> findUnreadByTeamId(UUID teamId);
    
    Message save(Message message);
    
    void markAsRead(UUID id);
    
    void markAllAsReadForTeam(UUID teamId);
    
    void delete(UUID id);
}
