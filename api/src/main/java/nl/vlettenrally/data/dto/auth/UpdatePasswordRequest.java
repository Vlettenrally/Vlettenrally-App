package nl.vlettenrally.data.dto.auth;

import io.quarkus.runtime.annotations.RegisterForReflection;

@RegisterForReflection
public record UpdatePasswordRequest(
    String currentPassword,
    String newPassword
) {}
