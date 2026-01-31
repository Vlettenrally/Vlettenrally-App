package nl.vlettenrally.client.exception;

/**
 * Exception for pre-registration API errors.
 */
public class PreRegistrationApiException extends Exception {
    public PreRegistrationApiException(String message) {
        super(message);
    }

    public PreRegistrationApiException(String message, Throwable cause) {
        super(message, cause);
    }
}
