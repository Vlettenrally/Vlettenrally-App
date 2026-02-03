package nl.vlettenrally.data.dto.game;

import java.util.UUID;

import io.quarkus.runtime.annotations.RegisterForReflection;

@RegisterForReflection
public record GameResponse(
    UUID id,
    String name,
    String description,
    String startTime,
    String endTime,
    String startingAreaActiveTime,
    String startSignalTime,
    String status,
    Integer externalEventId,
    String externalEventName,
    String locationEndpointUrl,
    Integer locationPollingFrequency,
    Boolean locationEndpointActive,
    String createdAt,
    String updatedAt
) {}
