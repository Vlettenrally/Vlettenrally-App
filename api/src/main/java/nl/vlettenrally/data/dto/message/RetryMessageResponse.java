package nl.vlettenrally.data.dto.message;

import io.quarkus.runtime.annotations.RegisterForReflection;

/**
 * Response DTO for message retry operation.
 */
@RegisterForReflection
public record RetryMessageResponse(
    int retriedCount,
    int successCount,
    int failedCount
) {}
