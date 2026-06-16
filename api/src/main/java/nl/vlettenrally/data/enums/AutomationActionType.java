package nl.vlettenrally.data.enums;

/**
 * What an automation rule does when it fires.
 */
public enum AutomationActionType {
    SEND_MESSAGE,           // Multi-channel message to the affected team(s), source AUTOMATION
    AWARD_POINTS,           // Ledger entry of type AUTOMATION_BONUS for the affected team(s)
    NOTIFY_ADMINS,          // Admin-dashboard WebSocket notification
    FINISH_TEAM             // Mark the affected team(s) finished: no further automatic points
}
