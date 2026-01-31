package nl.vlettenrally.data.dto.message;

import java.util.List;
import java.util.UUID;

/**
 * Request DTO for sending an admin message to teams.
 */
public record SendAdminMessageRequest(
    UUID gameId,
    List<UUID> teamIds,  // null or empty means send to all teams in the game
    String messageType,
    String content,
    String icon,
    boolean saveAsTemplate,
    String templateName
) {}
