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
    String status,
    Integer externalEventId,
    String externalEventName,
    String locationEndpointUrl,
    Integer locationPollingFrequency,
    Boolean locationEndpointActive,
    Integer locationPollingLeadMinutes,
    Integer locationPollingTailMinutes,
    /* Flat points deducted from a team that finishes after endTime. 0 / null = no penalty. */
    Integer lateFinishPenaltyPoints,
    // Per-game location-ingest guard overrides (null = inherit the global default).
    Double locMaxSpeedKmh,
    Double locBoatMaxSpeedKmh,
    Double locTurnAngleThresholdDegrees,
    Double locMinBearingSegmentMeters,
    Double locMaxAccuracyMeters,
    Long locStaleFixSeconds
) {}
