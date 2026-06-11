package nl.vlettenrally.service.util;

import nl.vlettenrally.data.domain.Game;

import java.time.Duration;
import java.time.Instant;

/**
 * Shifts an absolute timestamp from one game's timeframe into another's, used
 * when copying time-anchored data (automations, zones) between games.
 */
public final class GameTimeShifter {

    private GameTimeShifter() {
        // Utility class
    }

    /**
     * Shift an absolute timestamp by the gap between source and target for
     * whichever game anchor (start signal or end time) it sits closest to in
     * the source game. Leaves it unchanged when no usable anchor pair exists.
     */
    public static Instant adjust(Instant time, Game source, Game target) {
        if (time == null) {
            return null;
        }

        Instant best = null;
        long bestDistance = Long.MAX_VALUE;

        Instant[][] anchorPairs = {
            {source.getStartSignalTime(), target.getStartSignalTime()},
            {source.getEndTime(), target.getEndTime()},
        };
        for (Instant[] pair : anchorPairs) {
            Instant sourceAnchor = pair[0];
            Instant targetAnchor = pair[1];
            if (sourceAnchor == null || targetAnchor == null) {
                continue;
            }
            long distance = Math.abs(Duration.between(time, sourceAnchor).toMillis());
            if (distance < bestDistance) {
                bestDistance = distance;
                best = time.plus(Duration.between(sourceAnchor, targetAnchor));
            }
        }
        return best != null ? best : time;
    }
}
