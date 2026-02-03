package nl.vlettenrally.data.dto.game;

import io.quarkus.runtime.annotations.RegisterForReflection;

/**
 * Response DTO for location endpoint test results.
 */
@RegisterForReflection
public record LocationTestResponse(
        boolean success,
        int participantCount,
        String errorMessage
) {}
