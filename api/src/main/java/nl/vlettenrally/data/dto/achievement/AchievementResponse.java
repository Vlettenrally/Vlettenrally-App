package nl.vlettenrally.data.dto.achievement;

import java.util.UUID;

import io.quarkus.runtime.annotations.RegisterForReflection;

@RegisterForReflection
public record AchievementResponse(
    UUID id,
    UUID teamId,
    UUID checkpointId,
    int pointsEarned,
    String status,
    String detectedAt,
    String verifiedAt,
    String notes
) {}
