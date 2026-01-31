package nl.vlettenrally.data.dto.external;

/**
 * Response DTO for connection test results.
 */
public record ConnectionTestResponse(
        boolean success,
        String message
) {}
