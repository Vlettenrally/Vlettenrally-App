package nl.vlettenrally.data.dto.auth;

/**
 * The result of a refresh-token exchange.
 * <p>
 * The distinction that matters is {@link Retryable} vs {@link Rejected}. Both used to collapse into
 * an empty Optional and therefore into a 401, which every client reads as "your session is over" —
 * so a duplicate in-flight refresh, or a chain that was momentarily unresolvable, logged a team out
 * for good. Retryable says "ask again in a moment"; only Rejected ends a session.
 */
public sealed interface RefreshOutcome {

    record Success(TokenResult tokens) implements RefreshOutcome {}

    /** Transient: the caller should retry shortly with the token it already has. */
    record Retryable(String reason) implements RefreshOutcome {}

    /** Definitive: this refresh token will never work again. */
    record Rejected(String reason) implements RefreshOutcome {}
}
