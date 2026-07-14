package nl.vlettenrally.data.enums;

/**
 * How a team was checked in. Null on a team that is not checked in.
 *
 * <p>Combined with {@code checkedInDigitally} this distinguishes a team the rally desk
 * sent off to check in on their own phones but that never finished ({@code DIGITAL} +
 * not digitally checked in) from a team the desk fully checked in on their behalf
 * ({@code MANUAL}). Both share {@code checkedIn = true, checkedInDigitally = false}.
 */
public enum CheckInMethod {
    /** The rally desk filled in the team's details for them. Check-in is complete. */
    MANUAL,
    /** The team checks in on their own phones; complete once checkedInDigitally is set. */
    DIGITAL
}
