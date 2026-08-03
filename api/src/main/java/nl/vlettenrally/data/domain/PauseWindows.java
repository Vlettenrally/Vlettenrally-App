package nl.vlettenrally.data.domain;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * A game's emergency-stop intervals, as an immutable snapshot answering the only two
 * questions the scoring pipeline actually asks of them. Consumers take one snapshot per
 * operation and reuse it: a rebuild that walks thousands of fixes must not have a resume
 * land mid-walk, or the chain it produces is one neither live accrual nor a later rebuild
 * would produce.
 * <p>
 * Every window is half-open {@code [pausedAt, resumedAt)}, with a null {@code resumedAt}
 * meaning "still paused, extends to infinity". Keeping that rule in this one class is the
 * point of the type: the same boundary decision has to be made by distance accrual and by
 * backfill's checkpoint eligibility, and if the two ever disagreed by one fix the totals
 * would silently diverge.
 */
public record PauseWindows(List<GamePausePeriod> periods) {

    private static final PauseWindows NONE = new PauseWindows(List.of());

    public PauseWindows {
        periods = List.copyOf(periods);
    }

    /** The common case: a game that has never been paused. */
    public static PauseWindows none() {
        return NONE;
    }

    public boolean isEmpty() {
        return periods.isEmpty();
    }

    /** Whether {@code t} falls inside any window — i.e. the game was frozen at that instant. */
    public boolean contains(Instant t) {
        if (t == null) {
            return false;
        }
        for (GamePausePeriod p : periods) {
            if (!t.isBefore(p.getPausedAt())
                    && (p.getResumedAt() == null || t.isBefore(p.getResumedAt()))) {
                return true;
            }
        }
        return false;
    }

    /**
     * Whether any window overlaps the open interval {@code (from, to)} — i.e. a pause
     * happened between two counted fixes, so the travel between them must not be credited.
     * <p>
     * Stated as an overlap rather than "a pause started between them" so it still holds
     * if {@code from} itself sits inside a window, which a synthetic anchor (a migration
     * carryover seed) can do.
     */
    public boolean separates(Instant from, Instant to) {
        if (from == null || to == null) {
            return false;
        }
        for (GamePausePeriod p : periods) {
            boolean startsBeforeEnd = p.getPausedAt().isBefore(to);
            boolean endsAfterStart = p.getResumedAt() == null || p.getResumedAt().isAfter(from);
            if (startsBeforeEnd && endsAfterStart) {
                return true;
            }
        }
        return false;
    }

    /** The open period's id, if the game is paused right now. Diagnostics only. */
    public UUID openPeriodId() {
        return periods.stream()
                .filter(GamePausePeriod::isOpen)
                .map(GamePausePeriod::getId)
                .findFirst()
                .orElse(null);
    }
}
