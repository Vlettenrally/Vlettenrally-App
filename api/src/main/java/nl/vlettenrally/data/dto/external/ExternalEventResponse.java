package nl.vlettenrally.data.dto.external;

import io.quarkus.runtime.annotations.RegisterForReflection;

/**
 * Response DTO for external events from the pre-registration API.
 */
@RegisterForReflection
public record ExternalEventResponse(
        int eventId,
        String eventName,
        int bookingCount
) {}
