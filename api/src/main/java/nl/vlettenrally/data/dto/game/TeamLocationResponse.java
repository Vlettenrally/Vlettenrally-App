package nl.vlettenrally.data.dto.game;

import java.util.UUID;

import io.quarkus.runtime.annotations.RegisterForReflection;

/**
 * DTO for team location data returned by the locations API.
 */
@RegisterForReflection
public record TeamLocationResponse(
    UUID teamId,
    String teamName,
    double latitude,
    double longitude,
    Double speed,
    Double heading,
    String locationTime
) {}

