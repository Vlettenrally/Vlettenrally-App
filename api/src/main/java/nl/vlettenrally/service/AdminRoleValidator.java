package nl.vlettenrally.service;

import jakarta.enterprise.context.ApplicationScoped;
import nl.vlettenrally.data.domain.AdminRole;

import java.util.Set;

/**
 * Validates admin role combinations.
 * Enforces business rules for which roles can be combined.
 */
@ApplicationScoped
public class AdminRoleValidator {

    /**
     * Validates that the given set of roles is a valid combination.
     * 
     * Rule: VIEWER role cannot be combined with any other role.
     * 
     * @param roles Set of roles to validate
     * @return Validation error message, or null if valid
     */
    public String validateRoleCombination(Set<AdminRole> roles) {
        if (roles == null || roles.isEmpty()) {
            return "At least one role must be assigned";
        }

        // VIEWER cannot be combined with other roles
        if (roles.contains(AdminRole.VIEWER) && roles.size() > 1) {
            return "VIEWER role cannot be combined with other roles";
        }

        return null; // Valid
    }

    /**
     * Checks if the given roles contain the SUPER_ADMIN role.
     */
    public boolean isSuperAdmin(Set<AdminRole> roles) {
        return roles != null && roles.contains(AdminRole.SUPER_ADMIN);
    }
}
