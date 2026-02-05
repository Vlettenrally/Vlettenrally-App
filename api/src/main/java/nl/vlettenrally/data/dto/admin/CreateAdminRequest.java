package nl.vlettenrally.data.dto.admin;

import nl.vlettenrally.data.domain.AdminRole;

import java.util.Set;

import io.quarkus.runtime.annotations.RegisterForReflection;

/**
 * Request DTO for creating a new admin user
 */
@RegisterForReflection
public record CreateAdminRequest(
    String username,
    String password,
    Set<AdminRole> roles
) {}
