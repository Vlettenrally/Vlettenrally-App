package nl.vlettenrally.data.dto.auth;

public record AdminLoginResponse(
    String token,
    String adminId,
    String username,
    String expiresAt
) {}
