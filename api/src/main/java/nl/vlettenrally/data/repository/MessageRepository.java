package nl.vlettenrally.data.repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import nl.vlettenrally.data.domain.Message;
import nl.vlettenrally.data.enums.MessageSource;

/**
 * Repository interface for Message entities
 */
public interface MessageRepository {
    
    Optional<Message> findById(UUID id);
    
    List<Message> findAll();
    
    List<Message> findByTeamId(UUID teamId);
    
    List<Message> findUnreadByTeamId(UUID teamId);
    
    List<Message> findByGameId(UUID gameId);
    
    List<Message> findByGameIdAndSource(UUID gameId, MessageSource source);
    
    List<Message> findByGameIdAndSourcePaginated(UUID gameId, MessageSource source, int page, int pageSize);
    
    long countByGameIdAndSource(UUID gameId, MessageSource source);
    
    /**
     * Find all messages belonging to a batch (admin broadcast).
     */
    List<Message> findByBatchId(UUID batchId);
    
    /**
     * Find failed (undelivered) messages in a batch.
     */
    List<Message> findFailedByBatchId(UUID batchId);
    
    /**
     * Find all failed messages for a game (for retry-all functionality).
     */
    List<Message> findFailedByGameIdAndSource(UUID gameId, MessageSource source);
    
    /**
     * Find one representative message per batch for admin dashboard display.
     * Returns the first message of each batch, grouped by batchId.
     */
    List<Message> findDistinctBatchesByGameIdPaginated(UUID gameId, MessageSource source, int page, int pageSize);
    
    /**
     * Count distinct batches for a game.
     */
    long countDistinctBatchesByGameIdAndSource(UUID gameId, MessageSource source);

    /**
     * Count messages sent by an admin. Used to decide whether that admin can be
     * deleted outright without silently dropping the sender trail.
     */
    long countBySentByAdmin(UUID adminId);

    Message save(Message message);
    
    void markAsRead(UUID id);
    
    void markAllAsReadForTeam(UUID teamId);
    
    void delete(UUID id);
}
