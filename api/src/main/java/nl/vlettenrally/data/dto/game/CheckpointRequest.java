package nl.vlettenrally.data.dto.game;

import io.quarkus.runtime.annotations.RegisterForReflection;

/**
 * Request DTO for creating or updating a checkpoint.
 */
@RegisterForReflection
public record CheckpointRequest(
    String name,
    String openingTime,
    String closingTime,
    int points,
    Double latitude,
    Double longitude
) {}

