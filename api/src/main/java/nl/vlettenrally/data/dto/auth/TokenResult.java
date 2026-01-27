package nl.vlettenrally.data.dto.auth;

public record TokenResult(String accessToken, String refreshToken, long expiresIn, String subject, String name, String role) {}
