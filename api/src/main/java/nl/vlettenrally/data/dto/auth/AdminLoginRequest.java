package nl.vlettenrally.data.dto.auth;

public record AdminLoginRequest(
    String username,
    String password
) {}
