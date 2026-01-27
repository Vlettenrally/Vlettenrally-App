package nl.vlettenrally.data.dto.auth;

public record RefreshTokenData(
    String subject,
    String name,
    String role,
    long expiresAt
) {}
