package nl.vlettenrally.data.dto.admin;

import nl.vlettenrally.data.enums.Role;

import java.util.Set;

import io.quarkus.runtime.annotations.RegisterForReflection;

/**
 * Request DTO for updating admin roles
 */
@RegisterForReflection
public record UpdateAdminRolesRequest(
    Set<Role> roles
) {}
