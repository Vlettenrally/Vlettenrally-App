package nl.vlettenrally.data.dto.admin;

import io.quarkus.runtime.annotations.RegisterForReflection;

/**
 * Response DTO for password reset result
 */
@RegisterForReflection
public record ResetPasswordResponse(
    String temporaryPassword
) {}
