package nl.vlettenrally.data.dto.message;

/**
 * Response DTO for message retry operation.
 */
public record RetryMessageResponse(
    int retriedCount,
    int successCount,
    int failedCount
) {}
