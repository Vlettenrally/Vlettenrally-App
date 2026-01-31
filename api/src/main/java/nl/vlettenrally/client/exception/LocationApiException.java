package nl.vlettenrally.client.exception;

/**
 * Exception for location API errors.
 */
public class LocationApiException extends Exception {
    public LocationApiException(String message) {
        super(message);
    }

    public LocationApiException(String message, Throwable cause) {
        super(message, cause);
    }
}
