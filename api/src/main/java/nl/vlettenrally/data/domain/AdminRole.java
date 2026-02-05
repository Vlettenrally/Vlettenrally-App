package nl.vlettenrally.data.domain;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

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
    CHECKPOINT_MANAGER;
    
    /**
     * Serialize to lowercase for JSON output
     */
    @JsonValue
    public String toValue() {
        return name().toLowerCase();
    }
    
    /**
     * Deserialize from lowercase for JSON input
     */
    @JsonCreator
    public static AdminRole fromValue(String value) {
        if (value == null) {
            return null;
        }
        return AdminRole.valueOf(value.toUpperCase());
    }
}
