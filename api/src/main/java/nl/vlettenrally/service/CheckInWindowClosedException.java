package nl.vlettenrally.service;

/**
 * Thrown by {@link CheckInCoordinationService#registerDevice} when a device tries to
 * join a team through the regular game/team selection screen more than the configured
 * digital check-in window (default 30 min) after the team's physical check-in.
 * <p>
 * The blessed path — scanning the team's or an admin's invite QR — carries a signed
 * invite token that bypasses this gate, so it is not affected. The REST layer maps
 * this to HTTP 409 (not 403, which the frontend treats as an auth failure).
 */
public class CheckInWindowClosedException extends RuntimeException {

    public CheckInWindowClosedException(String message) {
        super(message);
    }
}
