package nl.vlettenrally.data.dto.game;

import io.quarkus.runtime.annotations.RegisterForReflection;

/**
 * Game-wide statistics for the admin Statistics page. When the game has zero
 * teams, all distance fields and counts are 0 — no NaN/Infinity from an
 * empty-stream average/min/max (see StatisticsService).
 */
@RegisterForReflection
public record GameStatisticsResponse(
    int totalTeams,
    int checkedInTeams,
    int disqualifiedTeams,
    double totalDistanceMeters,
    double averageDistanceMeters,
    double minDistanceMeters,
    double maxDistanceMeters,
    int totalCheckpoints,
    long checkpointVerifications,
    int totalPoints,
    int distanceBonusPoints,
    long earlyFinishBonusCount
) {}
