package nl.vlettenrally.data.dto.team;

import java.util.List;
import java.util.UUID;

/**
 * DTO for player's own team view.
 * Contains limited fields compared to TeamResponse - excludes sensitive admin data.
 */
public record TeamPlayerResponse(
    UUID id,
    UUID gameId,
    String name,
    String description,
    String boatNumber,
    boolean checkedIn,
    int totalPoints,
    List<UUID> rivals,
    String createdAt
) {}
