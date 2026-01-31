package nl.vlettenrally.data.enums;

/**
 * Source of a message, indicating how it was created.
 */
public enum MessageSource {
    /**
     * Message sent manually by an admin from the dashboard
     */
    ADMIN,
    
    /**
     * Message generated automatically by the system (achievements, checkpoints, etc.)
     */
    SYSTEM,
    
    /**
     * Message triggered by an automation rule
     */
    AUTOMATION
}
