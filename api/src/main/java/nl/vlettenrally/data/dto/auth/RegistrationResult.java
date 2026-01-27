package nl.vlettenrally.data.dto.auth;

public record RegistrationResult(String registrationToken, String teamId, long expiresAt) {}
