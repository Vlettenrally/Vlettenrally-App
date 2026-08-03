package nl.vlettenrally.data.domain;

import lombok.Getter;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

/**
 * One emergency stop of a game: the interval during which the competition was frozen.
 * A game may be paused and resumed any number of times, so these are periods rather
 * than a "paused since" field on the game itself — the distance chain has to be able
 * to reproduce, long after the fact, exactly which stretches did not count.
 * <p>
 * The interval is half-open {@code [pausedAt, resumedAt)}: a fix landing exactly on
 * {@code resumedAt} is already back in play. A null {@code resumedAt} means the pause
 * is still open, i.e. it extends to infinity. That convention is the whole reason
 * pause is safe to evaluate on any replica at any time — see {@link PauseWindows},
 * which is where consumers ask their questions rather than comparing timestamps
 * themselves.
 */
@Getter
@Setter
public class GamePausePeriod {
    private UUID id;
    private UUID gameId;
    private Instant pausedAt;
    private Instant resumedAt;
    private String reason;
    private UUID pausedBy;
    private Instant createdAt;

    public GamePausePeriod() {
        this.createdAt = Instant.now();
    }

    /** True while the game is still frozen by this period (never resumed). */
    public boolean isOpen() {
        return resumedAt == null;
    }
}
