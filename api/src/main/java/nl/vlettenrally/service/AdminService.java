package nl.vlettenrally.service;

import nl.vlettenrally.data.domain.Admin;
import nl.vlettenrally.data.dto.admin.AdminReferencesResponse;
import nl.vlettenrally.data.enums.Role;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * Service interface for Admin User Management.
 * Encapsulates business logic for admin operations, separating concerns from HTTP resource layer.
 */
public interface AdminService {

    /**
     * Get all admin users, active and deactivated alike — the admin-users page renders the
     * deactivated ones so they can be reactivated or deleted.
     *
     * @return List of all admin users
     */
    List<Admin> getAllAdmins();

    /**
     * Find an admin by ID.
     *
     * @param id The admin's UUID
     * @return Optional containing the admin if found
     */
    Optional<Admin> findAdminById(UUID id);

    /**
     * Find an admin by username.
     *
     * @param username The admin's username
     * @return Optional containing the admin if found
     */
    Optional<Admin> findAdminByUsername(String username);

    /**
     * Create a new admin with the provided credentials and roles.
     * Validates password strength and role combinations.
     *
     * @param username The username for the new admin
     * @param password The plain-text password (will be bcrypted)
     * @param roles The set of roles to assign
     * @return The created Admin
     * @throws IllegalArgumentException if validation fails
     */
    Admin createAdmin(String username, String password, Set<Role> roles);

    /**
     * Update an admin's roles with validation.
     * Prevents self-demotion from SUPER_ADMIN and ensures at least one SUPER_ADMIN remains.
     *
     * @param adminId The ID of the admin to update
     * @param newRoles The new set of roles
     * @param currentUserId The ID of the user making the change (for self-demotion check)
     * @return The updated Admin
     * @throws IllegalArgumentException if validation fails
     */
    Admin updateAdminRoles(UUID adminId, Set<Role> newRoles, UUID currentUserId);

    /**
     * Reset an admin's password to a temporary secure password.
     *
     * @param adminId The ID of the admin
     * @return The temporary password that was set
     * @throws IllegalArgumentException if admin not found
     */
    String resetAdminPassword(UUID adminId);

    /**
     * Deactivate an admin user (soft delete) and revoke its live tokens, so the account
     * stops working immediately rather than at the next access-token expiry.
     * Prevents self-deactivation and ensures at least one SUPER_ADMIN remains.
     *
     * @param adminId The ID of the admin to deactivate
     * @param currentUserId The ID of the user making the change (for self-deactivation check)
     * @return The deactivated Admin
     * @throws IllegalArgumentException if validation fails
     */
    Admin deactivateAdmin(UUID adminId, UUID currentUserId);

    /**
     * Reactivate a previously deactivated admin user. Idempotent for an already-active admin.
     * No last-SUPER_ADMIN rule applies — this only ever adds an active admin.
     *
     * @param adminId The ID of the admin to activate
     * @return The activated Admin
     * @throws IllegalArgumentException if the admin does not exist
     */
    Admin activateAdmin(UUID adminId);

    /**
     * Permanently delete an admin user. Only allowed for an already-deactivated admin with no
     * attributed content, so the audit trail on pictures, messages, achievements, point entries
     * and automation rules is never silently blanked out.
     *
     * @param adminId The ID of the admin to delete
     * @param currentUserId The ID of the user making the change (for self-delete check)
     * @throws IllegalArgumentException if the admin does not exist, or is the caller
     * @throws IllegalStateException if the admin is still active or still has attributed content
     */
    void deleteAdmin(UUID adminId, UUID currentUserId);

    /**
     * Count the content attributed to an admin, to show why it can or cannot be deleted.
     *
     * @param adminId The ID of the admin
     * @return The per-table reference counts
     * @throws IllegalArgumentException if the admin does not exist
     */
    AdminReferencesResponse countAdminReferences(UUID adminId);

    /**
     * Count the number of active SUPER_ADMIN users.
     *
     * @return The count of active SUPER_ADMIN users
     */
    long countActiveSuperAdmins();
}

