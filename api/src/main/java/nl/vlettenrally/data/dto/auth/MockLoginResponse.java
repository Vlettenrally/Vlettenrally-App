package nl.vlettenrally.data.dto.auth;

import io.quarkus.runtime.annotations.RegisterForReflection;

@RegisterForReflection
public record MockLoginResponse(
    String token,
    String expiresAt
) {}
