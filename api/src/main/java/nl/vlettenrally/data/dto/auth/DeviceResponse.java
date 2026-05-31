package nl.vlettenrally.data.dto.auth;

import io.quarkus.runtime.annotations.RegisterForReflection;

import java.time.Instant;
import java.util.UUID;

@RegisterForReflection
public record DeviceResponse(
    UUID deviceId,
    String nickname,
    boolean isMain,
    Instant createdAt,
    Instant lastSeen
) {}
