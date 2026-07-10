package nl.vlettenrally.service;

import io.quarkus.logging.Log;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import nl.vlettenrally.data.domain.LocationData;
import nl.vlettenrally.data.domain.TeamDistanceSegment;
import nl.vlettenrally.data.repository.LocationDataRepository;
import nl.vlettenrally.data.repository.TeamDistanceSegmentRepository;
import nl.vlettenrally.service.util.GeoDistance;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.locationtech.jts.geom.Point;

import java.time.Instant;
import java.util.UUID;

/**
 * Computes how far a team has travelled from its stored location fixes, replacing
 * the provider-reported distance.
 * <p>
 * State is the per-team chain of {@link TeamDistanceSegment} rows: the last segment's
 * endpoint is the anchor (the last <em>counted</em> fix) and the travelled distance is
 * simply the sum of segment meters. There is no separate accumulator to keep in sync —
 * appending or rebuilding segments IS the update.
 * <p>
 * Movement only accrues once a new fix is at least {@code minSegmentMeters} from the
 * anchor; smaller jumps are treated as GPS jitter and produce no segment, holding the
 * anchor. This yields the same total as walking the full track, while a moored boat's
 * wander accrues ~zero.
 * <p>
 * Both methods must run inside the caller's transaction: they take the per-team
 * advisory lock ({@link TeamDistanceSegmentRepository#lockTeam}) first, which
 * serializes concurrent updates from the Legendstracking poller, the Traccar/OsmAnd
 * endpoint and backfill's recompute for the same team.
 */
@ApplicationScoped
public class TeamDistanceCalculator {

    @Inject
    LocationDataRepository locationDataRepository;

    @Inject
    TeamDistanceSegmentRepository segmentRepository;

    @ConfigProperty(name = "vlettenrally.distance.min-segment-meters", defaultValue = "8")
    double minSegmentMeters;

    /**
     * Fold one just-stored live fix into the team's segment chain and return the
     * updated total travelled distance in metres. The fix is passed in-hand, so the
     * live path never re-reads {@code location_data}.
     */
    public double accrueFix(UUID teamId, LocationData fix) {
        segmentRepository.lockTeam(teamId);
        appendFix(teamId, segmentRepository.findLastByTeamId(teamId).orElse(null), fix);
        return segmentRepository.sumMetersByTeamId(teamId);
    }

    /**
     * Rebuild the tail of the team's segment chain after fixes were inserted into an
     * older gap (by a backfill): every segment from the earliest changed fix onward is
     * deleted and re-derived by walking the stored fixes, so the inserted points are
     * counted and the segments that previously spanned the gap are replaced. Segments
     * before the gap are untouched — no whole-track recompute.
     * <p>
     * A carryover seed (migrated pre-segment-table total) is never deleted: its meters
     * may predate the {@code location_data} retention window and cannot be re-derived.
     * The total can shrink slightly on a rebuild (adding vertices to a polyline almost
     * always lengthens it, but under the {@code minSegmentMeters} jitter threshold that
     * is not guaranteed); {@link AutomationEngine#onDistanceUpdate} absorbs a decrease
     * by rebaselining without credit, so no clamp is needed here.
     */
    public double recomputeAfter(UUID teamId, Instant earliestChangedTime) {
        segmentRepository.lockTeam(teamId);
        segmentRepository.deleteFromTime(teamId, earliestChangedTime);

        TeamDistanceSegment anchor = segmentRepository.findLastByTeamId(teamId).orElse(null);
        Instant after = anchor != null ? anchor.getToTime() : Instant.EPOCH;
        for (LocationData fix : locationDataRepository.findByTeamIdAfter(teamId, after)) {
            anchor = appendFix(teamId, anchor, fix);
        }
        return segmentRepository.sumMetersByTeamId(teamId);
    }

    /**
     * Apply the accrual rule for one fix against the current anchor (null when the
     * chain is empty): seed the chain on the first fix, append a segment once the fix
     * is at least {@code minSegmentMeters} from the anchor, or hold the anchor on
     * jitter. Returns the (possibly advanced) anchor.
     */
    private TeamDistanceSegment appendFix(UUID teamId, TeamDistanceSegment anchor, LocationData fix) {
        Point p = fix.getLocation();
        if (p == null) {
            // Ingest validation rejects these; skip defensively when walking stored fixes.
            return anchor;
        }

        if (anchor == null) {
            // First fix ever: seed the chain, no distance credited.
            return segmentRepository.save(seed(teamId, fix, p));
        }

        if (!fix.getLocationTime().isAfter(anchor.getToTime())) {
            // Out-of-order vs the anchor — only reachable in odd clock-skew corners; the
            // dt<=0 ingest guard already blocks this whenever prior fixes exist. Skip
            // rather than violate the chain's time-ordering constraint.
            Log.infof("Skipping non-advancing fix for team %s: %s is not after anchor %s",
                    teamId, fix.getLocationTime(), anchor.getToTime());
            return anchor;
        }

        double d = GeoDistance.haversineMeters(anchor.getToLon(), anchor.getToLat(), p.getX(), p.getY());
        if (d < minSegmentMeters) {
            // Jitter below the threshold — hold the anchor, no segment.
            return anchor;
        }

        TeamDistanceSegment segment = new TeamDistanceSegment();
        segment.setTeamId(teamId);
        segment.setFromTime(anchor.getToTime());
        segment.setFromLon(anchor.getToLon());
        segment.setFromLat(anchor.getToLat());
        segment.setToTime(fix.getLocationTime());
        segment.setToLon(p.getX());
        segment.setToLat(p.getY());
        segment.setMeters(d);
        return segmentRepository.save(segment);
    }

    private static TeamDistanceSegment seed(UUID teamId, LocationData fix, Point p) {
        TeamDistanceSegment segment = new TeamDistanceSegment();
        segment.setTeamId(teamId);
        segment.setToTime(fix.getLocationTime());
        segment.setToLon(p.getX());
        segment.setToLat(p.getY());
        segment.setMeters(0);
        return segment;
    }
}
