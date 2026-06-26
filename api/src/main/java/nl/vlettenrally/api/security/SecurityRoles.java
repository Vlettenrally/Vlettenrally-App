package nl.vlettenrally.api.security;

import java.util.Set;

/**
 * Central definition of the role strings used in {@code @RolesAllowed} annotations and
 * runtime {@code isUserInRole}/{@code hasRole} checks.
 * <p>
 * These must stay lowercase to match {@link nl.vlettenrally.data.enums.Role#toString()},
 * which is what ends up in the JWT {@code groups} claim.
 * <p>
 * {@link #STAFF} is a synthetic role: it is not stored on any user and never appears in a
 * JWT. It is derived at request time by {@link StaffRoleAugmentor} for any identity holding
 * at least one admin-type role, and means "any admin/staff user" (as opposed to a team).
 */
public final class SecurityRoles {

    private SecurityRoles() {
    }

    public static final String ADMIN = "admin";
    public static final String SUPER_ADMIN = "super_admin";
    public static final String VIEWER = "viewer";
    public static final String GAME_MASTER = "game_master";
    public static final String MESSAGE_SENDER = "message_sender";
    public static final String CHECKPOINT_MANAGER = "checkpoint_manager";
    public static final String RALLY_DESK = "rally_desk";
    public static final String TEAM = "team";
    public static final String CHECKIN = "checkin";

    /**
     * Synthetic "any admin-type user" role, added by {@link StaffRoleAugmentor}.
     */
    public static final String STAFF = "staff";

    /**
     * All admin-type roles. Holding any of these grants the derived {@link #STAFF} role.
     */
    public static final Set<String> ADMIN_ROLES = Set.of(
        ADMIN, SUPER_ADMIN, VIEWER, GAME_MASTER, MESSAGE_SENDER, CHECKPOINT_MANAGER, RALLY_DESK
    );
}
