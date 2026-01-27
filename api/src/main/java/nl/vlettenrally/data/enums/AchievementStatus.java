package nl.vlettenrally.data.enums;

/**
 * Status of an Achievement.
 */
public enum AchievementStatus {
    PENDING,    // Detected by system, awaiting game master verification
    VERIFIED,   // Confirmed by game master, points awarded
    REJECTED    // Rejected by game master (e.g., team didn't actually complete checkpoint)
}
