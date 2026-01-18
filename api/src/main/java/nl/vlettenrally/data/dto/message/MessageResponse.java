package nl.vlettenrally.data.dto.message;

import java.util.UUID;

public record MessageResponse(
    UUID id,
    UUID teamId,
    String messageType,
    String content,
    String icon,
    String sentAt,
    boolean delivered,
    boolean read
) {}
