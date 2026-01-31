package nl.vlettenrally.data.repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import nl.vlettenrally.data.domain.MessageDelivery;

/**
 * Repository interface for MessageDelivery entities
 */
public interface MessageDeliveryRepository {
    
    Optional<MessageDelivery> findById(UUID id);
    
    List<MessageDelivery> findByMessageId(UUID messageId);
    
    List<MessageDelivery> findByTeamId(UUID teamId);
    
    Optional<MessageDelivery> findByMessageIdAndTeamId(UUID messageId, UUID teamId);
    
    List<MessageDelivery> findFailedByMessageId(UUID messageId);
    
    List<MessageDelivery> findFailedByGameId(UUID gameId);
    
    MessageDelivery save(MessageDelivery delivery);
    
    void delete(UUID id);
}
