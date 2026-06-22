package nl.vlettenrally.data.enums;

/**
 * Moderation status of a team-uploaded picture.
 */
public enum PictureStatus {
    PENDING,    // Reserved/uploaded, awaiting admin moderation
    APPROVED,   // Approved by an admin, eligible for the public slideshow
    REJECTED    // Rejected by an admin (object retained for audit)
}
