package nl.vlettenrally.data.dto.game;

import java.util.UUID;

import io.quarkus.runtime.annotations.RegisterForReflection;

@RegisterForReflection
public record CheckpointResponse(
    UUID id,
    UUID gameId,
    String name,
    String openingTime,
    String closingTime,
    int points,
    Double latitude,
    Double longitude,
    String externalId,
    String zoneType,
    double radiusMeters,
    Double boundsNorth,
    Double boundsSouth,
    Double boundsEast,
    Double boundsWest
) {}
