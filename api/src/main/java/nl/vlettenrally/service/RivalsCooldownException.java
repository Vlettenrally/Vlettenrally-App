package nl.vlettenrally.service;

import lombok.Getter;

/**
 * Thrown by {@link TeamService#setRivals} when a team changes its rival list again before its
 * per-team cooldown window (see {@link RivalsCooldown}) has elapsed. The REST layer maps this to
 * HTTP 429 (Too Many Requests) with a {@code Retry-After} header derived from
 * {@link #getRetryAfterSeconds()}.
 */
@Getter
public class RivalsCooldownException extends RuntimeException {

    private final long retryAfterSeconds;

    public RivalsCooldownException(long retryAfterSeconds) {
        super("Rival list was changed too recently; try again in " + retryAfterSeconds + "s");
        this.retryAfterSeconds = retryAfterSeconds;
    }

}
