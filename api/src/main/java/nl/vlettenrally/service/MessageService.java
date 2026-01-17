package nl.vlettenrally.service;

import nl.vlettenrally.domain.Message;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Service interface for Message operations
 */
public interface MessageService {
    
    Optional<Message> findById(UUID id);
    
    List<Message> findAll();
    
    List<Message> findByTeamId(UUID teamId);
    
    List<Message> findUnreadByTeamId(UUID teamId);
    
    int countUnreadByTeamId(UUID teamId);
    
    Message send(UUID teamId, Message.MessageType type, String content, String icon);
    
    void markAsRead(UUID id);
    
    void markAllAsReadForTeam(UUID teamId);
    
    Message save(Message message);
    
    void delete(UUID id);
}
