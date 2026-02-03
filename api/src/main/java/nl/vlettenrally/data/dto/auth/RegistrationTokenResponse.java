package nl.vlettenrally.data.dto.auth;

import java.time.Instant;

import io.quarkus.runtime.annotations.RegisterForReflection;

@RegisterForReflection
public record RegistrationTokenResponse(
    String registrationToken,
    String teamId,
    Instant expiresAt
) {}
