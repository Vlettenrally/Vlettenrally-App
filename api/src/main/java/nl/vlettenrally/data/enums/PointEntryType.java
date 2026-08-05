package nl.vlettenrally.data.enums;

/**
 * Source of a PointEntry in the points ledger.
 */
public enum PointEntryType {
    CHECKPOINT_VERIFICATION,    // System-awarded points for a verified achievement
    MANUAL_ADJUSTMENT,          // Admin-initiated points change (correction or bonus)
    AUTOMATION_BONUS,           // Awarded by an automation rule's AWARD_POINTS action
    DISTANCE_BONUS,             // Continuous distance-traveled accrual (one growing entry per team)
    LATE_FINISH_PENALTY         // Flat deduction for finishing after the game's end time
}
