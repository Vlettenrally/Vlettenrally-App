package nl.vlettenrally.data.enums;

/**
 * Source of a PointEntry in the points ledger.
 */
public enum PointEntryType {
    CHECKPOINT_VERIFICATION,    // System-awarded points for a verified achievement
    MANUAL_ADJUSTMENT           // Admin-initiated points change (correction or bonus)
}
