package nl.vlettenrally.websockets;

/**
 * Result of a WebSocket token generation attempt.
 * Used by {@link WebSocketTokenService} to return either a success with the token,
 * or a specific error type.
 */
public sealed interface WebSocketTokenResult {

    /**
     * Token was generated and stored successfully.
     * @param token The generated token string
     * @param expiresInSeconds Time until token expires
     */
    record Success(String token, int expiresInSeconds) implements WebSocketTokenResult {}

    /**
     * The specified team was not found.
     */
    record TeamNotFound() implements WebSocketTokenResult {}

    /**
     * The team does not belong to the specified game.
     */
    record TeamGameMismatch() implements WebSocketTokenResult {}

    /**
     * A generic error occurred during token generation.
     * @param message Description of the error
     */
    record Error(String message) implements WebSocketTokenResult {}
}

