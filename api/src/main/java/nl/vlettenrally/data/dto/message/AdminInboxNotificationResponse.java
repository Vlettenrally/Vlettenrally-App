package nl.vlettenrally.data.dto.message;

import io.quarkus.runtime.annotations.RegisterForReflection;

import java.util.UUID;

@RegisterForReflection
public record AdminInboxNotificationResponse(
    UUID id,
    UUID gameId,
    UUID teamId,
    String teamName,
    String source,
    String content,
    String metadataJson,
    String createdAt,
    boolean read,
    String readAt,
    UUID readByAdminId
) {}
