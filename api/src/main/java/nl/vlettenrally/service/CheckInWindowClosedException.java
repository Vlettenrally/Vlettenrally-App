package nl.vlettenrally.service;

/**
 * Thrown by {@link CheckInCoordinationService#registerDevice} when an additional
 * phone tries to join a team through the regular game/team selection screen more
 * than the configured window (default 15 min) after the team's initial check-in.
 * <p>
 * The blessed path — scanning the team's or an admin's invite QR — carries a signed
 * invite token that bypasses this gate, so it is not affected. The REST layer maps
 * this to HTTP 403.
 */
public class CheckInWindowClosedException extends RuntimeException {

    public CheckInWindowClosedException(String message) {
        super(message);
    }
}
