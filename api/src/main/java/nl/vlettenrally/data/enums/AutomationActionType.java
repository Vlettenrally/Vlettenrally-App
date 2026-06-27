package nl.vlettenrally.data.enums;

/**
 * What an automation rule does when it fires.
 */
public enum AutomationActionType {
    SEND_MESSAGE,              // Multi-channel message to the affected team(s), source AUTOMATION
    AWARD_POINTS,              // Ledger entry of type AUTOMATION_BONUS for the affected team(s)
    NOTIFY_ADMINS,             // Admin-dashboard WebSocket notification
    FINISH_TEAM,               // Mark the affected team(s) finished: no further automatic points
    SET_RANKINGS_PUBLISHED,    // Set game.rankingsPublished true (publish) or false (hide)
    SET_PUBLIC_RANKINGS_ENABLED // Set game.publicRankingsEnabled true (enable) or false (disable)
}
