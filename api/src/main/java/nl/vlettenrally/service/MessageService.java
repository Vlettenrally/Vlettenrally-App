package nl.vlettenrally.service;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import nl.vlettenrally.data.enums.MessageType;

import nl.vlettenrally.data.domain.Message;
import nl.vlettenrally.data.dto.message.AdminMessageBatchResult;
import nl.vlettenrally.data.dto.message.AdminMessageResponse;

/**
 * Service interface for Message operations
 */
public interface MessageService {
    
    Optional<Message> findById(UUID id);
    
    List<Message> findAll();
    
    List<Message> findByTeamId(UUID teamId);
    
    List<Message> findUnreadByTeamId(UUID teamId);
    
    int countUnreadByTeamId(UUID teamId);
    
    Message send(UUID teamId, MessageType type, String content, String icon);
    
    /**
     * Send an admin message to specified teams or all teams in a game.
     * Creates one Message per team with a shared batchId for grouping.
     * 
     * @param gameId The game ID
     * @param teamIds List of team IDs to send to (null or empty for all teams in game)
     * @param type Message type
     * @param content Message content
     * @param icon Optional icon
     * @param adminId The admin ID who sent the message
     * @return Batch result with aggregated delivery statistics
     */
    AdminMessageBatchResult sendAdminMessage(UUID gameId, List<UUID> teamIds, MessageType type, String content, String icon, UUID adminId);
    
    /**
     * Get admin message batches for a game with pagination.
     * Returns one representative message per batch.
     */
    List<Message> findAdminMessagesByGame(UUID gameId, int page, int pageSize);
    
    /**
     * Count admin message batches for a game.
     */
    long countAdminMessagesByGame(UUID gameId);
    
    /**
     * Get all messages belonging to a batch (for delivery details view).
     */
    List<Message> getMessagesInBatch(UUID batchId);
    
    /**
     * Retry failed deliveries for a specific batch.
     * 
     * @return Number of successful retries
     */
    int retryFailedDeliveries(UUID batchId);
    
    /**
     * Retry all failed deliveries for a game.
     * 
     * @return Total number of successful retries
     */
    int retryAllFailedDeliveries(UUID gameId);
    
    void markAsRead(UUID id);
    
    /**
     * Mark multiple messages as read (batch operation).
     * 
     * @param ids List of message IDs to mark as read
     */
    void markMultipleAsRead(List<UUID> ids);
    
    void markAllAsReadForTeam(UUID teamId);
    
    Message save(Message message);
    
    void delete(UUID id);

    /**
     * Build an admin response from a batch result and its messages.
     */
    AdminMessageResponse buildAdminMessageResponse(AdminMessageBatchResult batchResult, List<Message> messages);

    /**
     * Build an admin response from a representative message and its batch messages.
     */
    AdminMessageResponse buildAdminMessageResponse(Message representativeMessage, List<Message> batchMessages);
}
