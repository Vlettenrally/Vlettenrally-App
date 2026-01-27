package nl.vlettenrally.data.dto.auth;

public record RegistrationTokenData(
    String subject,
    String name,
    String role,
    long expiresAt,
    boolean used) {}
