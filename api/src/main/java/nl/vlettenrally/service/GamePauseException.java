package nl.vlettenrally.service;

import jakarta.ws.rs.core.Response;
import lombok.Getter;

/**
 * Thrown by {@link GamePauseService} when a pause or resume is not legal for the game's
 * current state. The REST resource translates it into an ErrorResponse.
 */
@Getter
public class GamePauseException extends RuntimeException {

    private final int statusCode;

    public GamePauseException(Response.Status status, String message) {
        super(message);
        this.statusCode = status.getStatusCode();
    }
}
