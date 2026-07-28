package nl.vlettenrally.data.enums;

/**
 * What causes an automation rule to fire.
 */
public enum AutomationTriggerType {
    CHECKPOINT_ENTRY,       // Team enters a checkpoint geofence (verified pass)
    SCHEDULED_TIME,         // Absolute time, or offset relative to game start/end signal
    GAME_STATUS_CHANGE,     // Game transitions to ACTIVE or COMPLETED
    TEAM_INACTIVITY,        // No location updates for N minutes
    RIVAL_OVERTAKE,         // Team's totalPoints passes one of its rivals'
    DISTANCE_TRAVELED,      // Continuous accrual: 1 point per N meters (system rule only)
    SPEED_ALERT,            // Team's reported GPS speed exceeds a configured threshold
    TEAM_CHECK_IN,          // Team completes digital check-in
    PICTURE_APPROVED        // Team's uploaded picture was approved by a moderator
}
