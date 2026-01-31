package nl.vlettenrally.data.dto.game;

/**
 * Response DTO for location endpoint test results.
 */
public record LocationTestResponse(
        boolean success,
        int participantCount,
        String errorMessage
) {}
