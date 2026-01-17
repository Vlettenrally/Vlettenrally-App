package nl.vlettenrally.api.v1.Dto;

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
    String createdAt,
    String updatedAt
) {
    public TeamDto(GameDto game, String name, String description, String boatNumber, String color, List<String> participants, UUID deviceId, boolean checkedIn, int points, List<UUID> rivals, String notes, String createdAt, String updatedAt) {
        this(
            UUID.randomUUID(),
            game.id(),
            name,
            description,
            boatNumber,
            color,
            participants,
            deviceId,
            checkedIn,
            points,
            rivals,
            notes,
            createdAt,
            updatedAt
        );
    }
}
