package nl.vlettenrally.service;

import jakarta.ws.rs.core.Response;
import lombok.Getter;

/**
 * Thrown by {@link PictureService} to signal a client-facing error with a specific
 * HTTP status code. The REST resource translates it into an ErrorResponse.
 * <p>
 * Carries an int status code rather than {@link Response.Status} so non-enum codes
 * (e.g. 422 Unprocessable Entity) can be represented.
 */
@Getter
public class PictureException extends RuntimeException {

    private final int statusCode;

    public PictureException(int statusCode, String message) {
        super(message);
        this.statusCode = statusCode;
    }

    public PictureException(Response.Status status, String message) {
        this(status.getStatusCode(), message);
    }

}
