package nl.vlettenrally.data.dto.external;

/**
 * Response DTO for external events from the pre-registration API.
 */
public record ExternalEventResponse(
        int eventId,
        String eventName,
        int bookingCount
) {}
