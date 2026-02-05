package nl.vlettenrally.data.dto.admin;

import nl.vlettenrally.data.domain.AdminRole;

import java.util.Set;

import io.quarkus.runtime.annotations.RegisterForReflection;

/**
 * Request DTO for updating admin roles
 */
@RegisterForReflection
public record UpdateAdminRolesRequest(
    Set<AdminRole> roles
) {}
