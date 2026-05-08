package nl.vlettenrally.websockets.dto;

import io.quarkus.runtime.annotations.RegisterForReflection;

import java.util.UUID;

@RegisterForReflection
public record CheckpointDetectionPayload(
    UUID achievementId,
    UUID teamId,
    String teamName,
    UUID checkpointId,
    String checkpointName,
    String detectedAt
) {}
