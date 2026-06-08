package nl.vlettenrally.data.dto.game;

import java.time.Instant;

/**
 * Request DTO for creating or updating a game
 */
public record GameRequest(
    String name,
    String description,
    String participantInfo,
    Instant startTime,
    Instant endTime,
    Instant checkInStartTime,
    Instant startSignalTime,
    String status,
    Integer externalEventId,
    String externalEventName,
    String locationEndpointUrl,
    Integer locationPollingFrequency,
    Boolean locationEndpointActive
) {}
