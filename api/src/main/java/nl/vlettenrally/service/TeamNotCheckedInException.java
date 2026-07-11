package nl.vlettenrally.service;

/**
 * Thrown by {@link CheckInCoordinationService#registerDevice} when a device tries to
 * register for a team that has not yet been physically checked in at the rally desk
 * ({@code isCheckedIn() == false}).
 * <p>
 * Physical check-in is a hard prerequisite for digital check-in and is NOT bypassed by
 * an invite token — an invite lets a teammate join a team that is already present, it
 * does not stand in for the team being present. The REST layer maps this to HTTP 409
 * (not 403, which the frontend treats as an auth failure).
 */
public class TeamNotCheckedInException extends RuntimeException {

    public TeamNotCheckedInException(String message) {
        super(message);
    }
}
