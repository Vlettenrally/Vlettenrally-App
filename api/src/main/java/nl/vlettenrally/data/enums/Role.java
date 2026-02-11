package nl.vlettenrally.data.enums;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

/**
 * All roles in the system - both admin and team roles.
 * Admins can have multiple roles, teams have a single TEAM role.
 */
public enum Role {
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
    CHECKPOINT_MANAGER,
    
    /**
     * Team role - teams can participate in games and check in
     */
    TEAM,

    /**
     * Checkin role - used to register a team before starting a game
     */
    CHECKIN;

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
    public static Role fromValue(String value) {
        if (value == null) {
            return null;
        }
        return Role.valueOf(value.toUpperCase());
    }

    @Override
    public String toString() {
        return name().toLowerCase();
    }
}
