package nl.vlettenrally.data.dto.message;

/**
 * Request DTO for creating or updating a message template.
 */
public record MessageTemplateRequest(
    String name,
    String messageType,
    String content,
    String icon
) {}
