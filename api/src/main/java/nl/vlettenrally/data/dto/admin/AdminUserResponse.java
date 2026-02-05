package nl.vlettenrally.data.dto.admin;

import nl.vlettenrally.data.domain.AdminRole;

import java.time.Instant;
import java.util.Set;
import java.util.UUID;

import io.quarkus.runtime.annotations.RegisterForReflection;

/**
 * Response DTO for Admin user information
 */
@RegisterForReflection
public record AdminUserResponse(
    UUID id,
    String username,
    Set<AdminRole> roles,
    boolean active,
    Instant createdAt,
    Instant updatedAt
) {}
