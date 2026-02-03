package nl.vlettenrally.data.dto.message;

import java.util.UUID;

import io.quarkus.runtime.annotations.RegisterForReflection;

/**
 * Response DTO for message template.
 */
@RegisterForReflection
public record MessageTemplateResponse(
    UUID id,
    String name,
    String messageType,
    String content,
    String icon,
    String createdAt,
    String updatedAt
) {}
