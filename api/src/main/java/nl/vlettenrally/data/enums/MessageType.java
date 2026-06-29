package nl.vlettenrally.data.enums;

/**
 * Type of a Message.
 */
public enum MessageType {
    GENERAL,
    COUNTDOWN,
    CHECKPOINT,
    RALLYDESK,
    ACHIEVEMENT,
    RIVAL,
    SPEED_WARNING;

    /**
     * Check if this message type is critical and should trigger SMS fallback
     * when the team's device is inactive.
     *
     * @return true if this is a critical message type
     */
    public boolean isCritical() {
        return this == RALLYDESK;
    }
}
