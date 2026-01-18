package nl.vlettenrally.data.dto.auth;

import java.time.Instant;

public record RegistrationTokenResponse(
    String registrationToken,
    String teamId,
    Instant expiresAt
) {}
