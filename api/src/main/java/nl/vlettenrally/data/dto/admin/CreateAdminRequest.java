package nl.vlettenrally.data.dto.admin;

import nl.vlettenrally.data.enums.Role;

import java.util.Set;

import io.quarkus.runtime.annotations.RegisterForReflection;

/**
 * Request DTO for creating a new admin user
 */
@RegisterForReflection
public record CreateAdminRequest(
    String username,
    String password,
    Set<Role> roles
) {}
