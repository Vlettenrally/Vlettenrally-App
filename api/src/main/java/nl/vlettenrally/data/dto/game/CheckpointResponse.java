package nl.vlettenrally.data.dto.game;

import java.util.UUID;

public record CheckpointResponse(
    UUID id,
    UUID gameId,
    String name,
    String openingTime,
    String closingTime,
    int points,
    Double latitude,
    Double longitude
) {}
