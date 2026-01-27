package nl.vlettenrally.data.dto.auth;

public record AccessTokenData(
    String subject,
    String name,
    String role,
    long expiresAt) {}
