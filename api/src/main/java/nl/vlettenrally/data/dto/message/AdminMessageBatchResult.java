package nl.vlettenrally.data.dto.message;

import java.time.Instant;
import java.util.UUID;

/**
 * Result DTO returned when sending an admin message batch.
 * Contains aggregated delivery statistics for all teams in the batch.
 */
public record AdminMessageBatchResult(
    UUID batchId,
    int totalTeams,
    int deliveredCount,
    int failedCount,
    Instant sentAt
) {}
