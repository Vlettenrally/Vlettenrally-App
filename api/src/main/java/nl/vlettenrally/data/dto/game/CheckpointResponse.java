package nl.vlettenrally.data.dto.game;

import java.util.List;
import java.util.UUID;

import io.quarkus.runtime.annotations.RegisterForReflection;

/**
 * Response DTO for checkpoint data.
 * polygonPoints is a list of [lat, lng] pairs defining the polygon vertices (excludes closing point).
 */
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
    List<List<Double>> polygonPoints,
    boolean hidden
) {}
