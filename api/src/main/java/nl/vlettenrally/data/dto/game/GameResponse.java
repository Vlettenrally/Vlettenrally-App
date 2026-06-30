package nl.vlettenrally.data.dto.game;

import java.util.UUID;

import io.quarkus.runtime.annotations.RegisterForReflection;

@RegisterForReflection
public record GameResponse(
    UUID id,
    String name,
    String description,
    String participantInfo,
    String startTime,
    String endTime,
    String checkInStartTime,
    String startSignalTime,
    String status,
    Integer externalEventId,
    String externalEventName,
    String locationEndpointUrl,
    Integer locationPollingFrequency,
    Boolean locationEndpointActive,
    Integer locationPollingLeadMinutes,
    Integer locationPollingTailMinutes,
    // Per-game location-ingest guard overrides (null = inherit the global default).
    Double locMaxSpeedKmh,
    Double locBoatMaxSpeedKmh,
    Double locTurnAngleThresholdDegrees,
    Double locMinBearingSegmentMeters,
    Double locMaxAccuracyMeters,
    Long locStaleFixSeconds,
    Boolean rankingsPublished,
    Boolean publicRankingsEnabled,
    String createdAt,
    String updatedAt
) {}
