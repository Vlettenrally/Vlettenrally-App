package nl.vlettenrally.service.util;

import nl.vlettenrally.data.domain.LocationData;
import org.locationtech.jts.geom.Point;

import java.math.BigDecimal;
import java.time.Duration;

/**
 * Pure, DB-free decision logic that decides whether an incoming location fix is a
 * plausible continuation of a team's track or a GPS outlier to drop.
 * <p>
 * The trackers sit on slow-moving boats (typically &lt; 20 km/h) and fixes arrive every
 * ~30 s, so a fix implying a large jump is almost certainly noise. The filter is an
 * <em>online</em> reject-only filter: it looks only backwards (the last accepted fix
 * {@code last} and the one before it {@code prev}) and never alters coordinates. When a
 * fix is dropped the caller keeps {@code last}/{@code prev} anchored, so the track freezes
 * at the last good point and re-acquires on the next clean fix.
 * <p>
 * Direction (turn angle) alone cannot distinguish a genuine sharp turn from a spike, so it
 * is only acted on <em>together with</em> an implausible boat speed: a real hard turn keeps
 * the implied speed near boat speed, whereas a spike shows both a sharp reversal and a high
 * implied speed.
 */
public final class TrackOutlierFilter {

    private TrackOutlierFilter() {}

    /** Outcome of evaluating a candidate fix. Anything other than ACCEPT means drop it. */
    public enum Decision {
        ACCEPT,
        DROP_THROTTLE,
        DROP_ACCURACY,
        DROP_TELEPORT,
        DROP_SPIKE
    }

    /**
     * Tunable thresholds, passed in so this class stays free of CDI/config and trivially
     * unit-testable.
     *
     * @param minIntervalSeconds        drop fixes arriving faster than this since {@code last}
     * @param maxAccuracyMeters         drop fixes whose reported accuracy exceeds this (null bypasses)
     * @param maxSpeedKmh               hard teleport ceiling: any implied speed above this drops
     * @param boatMaxSpeedKmh           soft ceiling used by the direction-gated spike rule
     * @param turnAngleThresholdDegrees a turn beyond this is treated as a sharp reversal
     * @param minBearingSegmentMeters   legs shorter than this give unreliable bearings; skip the turn test
     * @param staleFixSeconds           if the gap exceeds this, apply only the hard ceiling (re-acquire after an outage)
     */
    public record Thresholds(
            long minIntervalSeconds,
            double maxAccuracyMeters,
            double maxSpeedKmh,
            double boatMaxSpeedKmh,
            double turnAngleThresholdDegrees,
            double minBearingSegmentMeters,
            long staleFixSeconds) {
    }

    /**
     * Decide whether {@code candidate} should be accepted.
     *
     * @param prev      the fix before {@code last}, or null if only one prior fix exists
     * @param last      the most recent accepted fix (must be non-null)
     * @param candidate the incoming fix
     * @param t         tuning thresholds
     */
    public static Decision evaluate(LocationData prev, LocationData last, LocationData candidate, Thresholds t) {
        long dtSeconds = Duration.between(last.getLocationTime(), candidate.getLocationTime()).getSeconds();
        if (dtSeconds < t.minIntervalSeconds()) {
            return Decision.DROP_THROTTLE;
        }

        BigDecimal accuracy = candidate.getAccuracy();
        if (accuracy != null && accuracy.doubleValue() > t.maxAccuracyMeters()) {
            return Decision.DROP_ACCURACY;
        }

        double dist = GeoDistance.haversineMeters(last.getLocation(), candidate.getLocation());
        double impliedKmh = (dist / dtSeconds) * 3.6;

        // After a long gap (e.g. a GPS outage) the boat may genuinely have moved far; only the
        // hard ceiling applies so the track can re-acquire rather than rejecting the catch-up fix.
        boolean stale = dtSeconds > t.staleFixSeconds();

        if (impliedKmh > t.maxSpeedKmh()) {
            return Decision.DROP_TELEPORT;
        }
        if (stale) {
            return Decision.ACCEPT;
        }

        // Direction-gated spike rejection: only when both legs are long enough for a meaningful
        // bearing, and only when a sharp reversal coincides with an implausible boat speed.
        if (prev != null) {
            double segIn = GeoDistance.haversineMeters(prev.getLocation(), last.getLocation());
            if (segIn >= t.minBearingSegmentMeters() && dist >= t.minBearingSegmentMeters()) {
                Point pPrev = prev.getLocation();
                Point pLast = last.getLocation();
                Point pCand = candidate.getLocation();
                double bearingIn = GeoDistance.bearingDegrees(pPrev, pLast);
                double bearingOut = GeoDistance.bearingDegrees(pLast, pCand);
                double turn = GeoDistance.turnAngleDegrees(bearingIn, bearingOut);
                if (turn > t.turnAngleThresholdDegrees() && impliedKmh > t.boatMaxSpeedKmh()) {
                    return Decision.DROP_SPIKE;
                }
            }
        }

        return Decision.ACCEPT;
    }
}
