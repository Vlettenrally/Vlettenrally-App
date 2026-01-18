package nl.vlettenrally.data.dto.auth;

public record MockLoginResponse(
    String token,
    String expiresAt
) {}
