package nl.vlettenrally.data.dto.auth;

public record MockUserResponse(
    String id,
    String name,
    String role,
    String gameId
) {}
