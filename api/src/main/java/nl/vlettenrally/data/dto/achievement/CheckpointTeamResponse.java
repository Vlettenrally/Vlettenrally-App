package nl.vlettenrally.data.dto.achievement;

import io.quarkus.runtime.annotations.RegisterForReflection;

import java.util.UUID;

@RegisterForReflection
public record CheckpointTeamResponse(
    UUID teamId,
    String teamName,
    String detectedAt,
    String achievementStatus,
    UUID achievementId,
    int defaultPoints,
    Integer pointsEarned,
    String verifiedAt,
    String notes
) {}
