package nl.vlettenrally.data.Dto;

import java.util.UUID;

public record GameDto (
        UUID id,
        String name,
        String description,
        String startTime,
        String endTime,
        String startingAreaActiveTime,
        String startSignalTime,
        String status) {}
