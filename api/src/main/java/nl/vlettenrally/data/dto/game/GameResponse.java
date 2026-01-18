package nl.vlettenrally.data.dto.game;

import java.util.UUID;

public record GameResponse(
    UUID id,
    String name,
    String description,
    String startTime,
    String endTime,
    String startingAreaActiveTime,
    String startSignalTime,
    String status
) {}
