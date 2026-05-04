package nl.vlettenrally.data.dto.game;

import io.quarkus.runtime.annotations.RegisterForReflection;

import java.util.List;

/**
 * Request DTO for creating or updating a checkpoint.
 * polygonPoints is a list of [lat, lng] pairs defining the polygon vertices.
 */
@RegisterForReflection
public record CheckpointRequest(
    String name,
    String openingTime,
    String closingTime,
    int points,
    Double latitude,
    Double longitude,
    String zoneType,
    Double radiusMeters,
    List<List<Double>> polygonPoints
) {}
