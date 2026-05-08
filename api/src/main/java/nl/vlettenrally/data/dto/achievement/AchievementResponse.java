package nl.vlettenrally.data.dto.achievement;

import java.util.UUID;

import io.quarkus.runtime.annotations.RegisterForReflection;

@RegisterForReflection
public record AchievementResponse(
    UUID id,
    UUID teamId,
    UUID checkpointId,
    Integer pointsEarned,
    int bonusPoints,
    String status,
    String detectedAt,
    String verifiedAt,
    String notes
) {}
