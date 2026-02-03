package nl.vlettenrally.data.dto.auth;

import io.quarkus.runtime.annotations.RegisterForReflection;

@RegisterForReflection
public record AdminLoginResponse(
    String token,
    String adminId,
    String username,
    String expiresAt
) {}
