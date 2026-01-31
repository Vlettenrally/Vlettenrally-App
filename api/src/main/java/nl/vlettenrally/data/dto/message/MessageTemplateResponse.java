package nl.vlettenrally.data.dto.message;

import java.util.UUID;

/**
 * Response DTO for message template.
 */
public record MessageTemplateResponse(
    UUID id,
    String name,
    String messageType,
    String content,
    String icon,
    String createdAt,
    String updatedAt
) {}
