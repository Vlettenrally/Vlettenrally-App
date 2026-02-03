package nl.vlettenrally.data.dto.auth;

import io.quarkus.runtime.annotations.RegisterForReflection;

@RegisterForReflection
public record MockUserResponse(
    String id,
    String name,
    String role,
    String gameId
) {}
