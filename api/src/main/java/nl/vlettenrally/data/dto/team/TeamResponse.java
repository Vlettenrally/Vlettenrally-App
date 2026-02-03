package nl.vlettenrally.data.dto.team;

import java.util.List;
import java.util.UUID;

import io.quarkus.runtime.annotations.RegisterForReflection;

@RegisterForReflection
public record TeamResponse(
    UUID id,
    UUID gameId,
    String name,
    String description,
    String boatNumber,
    List<String> participants,
    UUID deviceId,
    boolean checkedIn,
    int totalPoints,
    List<UUID> rivals,
    String notes,
    List<String> emergencyPhoneNumbers,
    String createdAt,
    String updatedAt
) {}
