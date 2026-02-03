package nl.vlettenrally.data.dto.message;

import java.util.UUID;

import io.quarkus.runtime.annotations.RegisterForReflection;

@RegisterForReflection
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
