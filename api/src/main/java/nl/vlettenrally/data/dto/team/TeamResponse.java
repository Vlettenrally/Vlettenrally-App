package nl.vlettenrally.data.dto.team;

import java.util.List;
import java.util.UUID;

import io.quarkus.runtime.annotations.RegisterForReflection;
import nl.vlettenrally.data.enums.CheckInMethod;

@RegisterForReflection
public record TeamResponse(
    UUID id,
    UUID gameId,
    String name,
    String description,
    List<String> participants,
    UUID deviceId,
    boolean checkedIn,
    boolean checkedInDigitally,
    CheckInMethod checkInMethod,
    boolean finished,
    boolean traccarEnabled,
    String traccarIdentifier,
    String trackerId,
    int totalPoints,
    List<UUID> rivals,
    String notes,
    List<String> emergencyPhoneNumbers,
    String createdAt,
    String updatedAt
) {}
