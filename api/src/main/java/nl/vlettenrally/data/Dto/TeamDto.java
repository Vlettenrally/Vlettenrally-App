package nl.vlettenrally.data.Dto;

import java.util.List;
import java.util.UUID;

public record TeamDto (
    UUID id,
    UUID game,
    String name,
    String description,
    String boatNumber,
    String color,
    List<String> participants,
    UUID deviceId,
    boolean checkedIn,
    int points,
    List<UUID> rivals,
    String notes,
    List<String> emergencyPhoneNumbers,
    String createdAt,
    String updatedAt
) {}
