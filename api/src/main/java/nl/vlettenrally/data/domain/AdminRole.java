package nl.vlettenrally.data.domain;

/**
 * Roles that can be assigned to admin users.
 * Admins can have multiple roles.
 */
public enum AdminRole {
    /**
     * Full administrative access to all features
     */
    ADMIN,
    
    /**
     * Ability to manage other admin users (create, modify, deactivate)
     */
    SUPER_ADMIN,
    
    /**
     * Read-only access. Cannot be combined with any other role.
     */
    VIEWER,
    
    /**
     * Can manage games and teams
     */
    GAME_MASTER,
    
    /**
     * Can send messages and manage message templates
     */
    MESSAGE_SENDER,
    
    /**
     * Can validate checkpoint attendance and award extra points
     */
    CHECKPOINT_MANAGER
}
