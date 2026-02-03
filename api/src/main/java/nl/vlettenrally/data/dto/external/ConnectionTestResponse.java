package nl.vlettenrally.data.dto.external;

import io.quarkus.runtime.annotations.RegisterForReflection;

/**
 * Response DTO for connection test results.
 */
@RegisterForReflection
public record ConnectionTestResponse(
        boolean success,
        String message
) {}
