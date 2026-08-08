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
    String status,
    Integer externalEventId,
    String externalEventName,
    String locationEndpointUrl,
    Integer locationPollingFrequency,
    Boolean locationEndpointActive,
    Integer locationPollingLeadMinutes,
    Integer locationPollingTailMinutes,
    /* Flat points deducted from a team that finishes after endTime. 0 = no penalty. */
    Integer lateFinishPenaltyPoints,
    /* Flat points awarded to a team finishing within earlyFinishBonusMinutes of endTime. 0 = no bonus. */
    Integer earlyFinishBonusPoints,
    /* Width, in minutes before endTime, of the early-finish bonus window. */
    Integer earlyFinishBonusMinutes,
    // Per-game location-ingest guard overrides (null = inherit the global default).
    Double locMaxSpeedKmh,
    Double locBoatMaxSpeedKmh,
    Double locTurnAngleThresholdDegrees,
    Double locMinBearingSegmentMeters,
    Double locMaxAccuracyMeters,
    Long locStaleFixSeconds,
    Boolean rankingsPublished,
    Boolean publicRankingsEnabled,
    /* Why the rally was stopped. Only set while the game is paused. */
    String pauseReason,
    String createdAt,
    String updatedAt
) {}
