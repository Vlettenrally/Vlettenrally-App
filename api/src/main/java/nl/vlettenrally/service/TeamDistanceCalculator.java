package nl.vlettenrally.service;

import io.quarkus.logging.Log;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import nl.vlettenrally.data.domain.LocationData;
import nl.vlettenrally.data.domain.TeamDistanceProgress;
import nl.vlettenrally.data.repository.LocationDataRepository;
import nl.vlettenrally.data.repository.TeamDistanceProgressRepository;
import nl.vlettenrally.service.util.GeoDistance;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.locationtech.jts.geom.Point;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Computes how far a team has travelled from its stored location fixes, replacing
 * the provider-reported distance.
 * <p>
 * State is kept per team in {@link TeamDistanceProgress}: a held "anchor" (the last
 * counted fix), the running cumulative metres, and a cursor (the GPS time of the
 * last consumed fix). Each call reads only the fixes newer than the cursor, so the
 * cost per poll is proportional to the handful of new fixes — not the whole track.
 * <p>
 * Movement only accrues once a new fix is at least {@code minSegmentMeters} from the
 * anchor; smaller jumps are treated as GPS jitter and the anchor is held. Because the
 * anchor is persisted, this yields the same total as walking the full track, while a
 * moored boat's wander accrues ~zero.
 * <p>
 * The returned value is a monotonically non-decreasing cumulative metre count, which
 * is exactly what {@link AutomationEngine#onDistanceUpdate} expects.
 */
@ApplicationScoped
public class TeamDistanceCalculator {

    @Inject
    LocationDataRepository locationDataRepository;

    @Inject
    TeamDistanceProgressRepository progressRepository;

    @ConfigProperty(name = "vlettenrally.distance.min-segment-meters", defaultValue = "8")
    double minSegmentMeters;

    /**
     * Accumulate any new fixes for the team and return the updated cumulative
     * travelled distance in metres.
     */
    public double computeTraveledMeters(UUID teamId) {
        TeamDistanceProgress progress = progressRepository.findByTeamId(teamId)
                .orElseGet(() -> newProgress(teamId));

        Instant after = progress.getLastProcessedTime() != null
                ? progress.getLastProcessedTime() : Instant.EPOCH;
        List<LocationData> newFixes = locationDataRepository.findByTeamIdAfter(teamId, after);
        if (newFixes.isEmpty()) {
            return progress.getCumulativeMeters();
        }

        Double anchorLon = progress.getAnchorLon();
        Double anchorLat = progress.getAnchorLat();
        double cumulative = progress.getCumulativeMeters();
        Instant cursor = progress.getLastProcessedTime();

        for (LocationData fix : newFixes) {
            Point p = fix.getLocation();
            if (p != null) {
                if (anchorLon == null || anchorLat == null) {
                    // First fix ever: seed the anchor, no distance credited.
                    anchorLon = p.getX();
                    anchorLat = p.getY();
                } else {
                    double d = GeoDistance.haversineMeters(anchorLon, anchorLat, p.getX(), p.getY());
                    if (d >= minSegmentMeters) {
                        cumulative += d;
                        anchorLon = p.getX();
                        anchorLat = p.getY();
                    }
                    // else: jitter below the threshold — hold the anchor.
                }
            }
            cursor = fix.getLocationTime();
        }

        progress.setAnchorLon(anchorLon);
        progress.setAnchorLat(anchorLat);
        progress.setCumulativeMeters(cumulative);
        progress.setLastProcessedTime(cursor);
        progress.setUpdatedAt(Instant.now());
        progressRepository.save(progress);

        return cumulative;
    }

    /**
     * Rewind the team's distance accumulator and re-walk its entire stored track, so points
     * inserted into an <em>older</em> gap (e.g. by a backfill) are counted — the incremental
     * {@link #computeTraveledMeters} would otherwise skip them because they predate the cursor.
     * <p>
     * The returned (and persisted) cumulative is clamped to never fall below the previous total:
     * {@link AutomationEngine#onDistanceUpdate} treats a decrease as a rebaseline and would
     * double-credit meters as the value climbed back. Adding vertices to a polyline almost always
     * increases its length, but under the {@code minSegmentMeters} jitter threshold that is not
     * guaranteed, so we clamp. The recomputed anchor is kept intact (only the scalar is clamped),
     * so subsequent live increments stay correct.
     * <p>
     * O(n) over the whole track; acceptable because backfill is rare and throttled.
     */
    public double recomputeFromScratch(UUID teamId) {
        TeamDistanceProgress progress = progressRepository.findByTeamId(teamId)
                .orElseGet(() -> newProgress(teamId));
        double oldCumulative = progress.getCumulativeMeters();

        // Reset the anchor + cursor so computeTraveledMeters re-walks from the first fix.
        progress.setAnchorLon(null);
        progress.setAnchorLat(null);
        progress.setCumulativeMeters(0);
        progress.setLastProcessedTime(null);
        progress.setUpdatedAt(Instant.now());
        progressRepository.save(progress);

        double recomputed = computeTraveledMeters(teamId);
        if (recomputed >= oldCumulative) {
            return recomputed;
        }

        // A full, independent re-derivation from location_data came out lower than what was
        // already credited. A shortfall up to a couple of minSegmentMeters can happen legitimately
        // (per the jitter-threshold caveat above, and floating-point rounding when the two are
        // really equal); anything past that margin is not explainable by either and indicates
        // prior drift/corruption of the accumulator (e.g. a cursor regression) — since re-walking
        // the same or more fixes can never legitimately produce a materially shorter path. The
        // clamp below applies either way to keep the fed value monotonic, but only log past the
        // margin so routine, expected variance doesn't drown out a real signal.
        double shortfall = oldCumulative - recomputed;
        if (shortfall > 2 * minSegmentMeters) {
            Log.warnf("Team %s distance recompute came out lower than stored total (recomputed=%.1fm, stored=%.1fm) "
                    + "— clamping to stored value; this indicates prior drift/corruption in team_distance_progress",
                teamId, recomputed, oldCumulative);
        }

        // Clamp the scalar up to the prior total to keep the fed value monotonic; leave the
        // recomputed anchor/cursor in place for correct future increments.
        TeamDistanceProgress clamped = progressRepository.findByTeamId(teamId).orElse(progress);
        clamped.setCumulativeMeters(oldCumulative);
        clamped.setUpdatedAt(Instant.now());
        progressRepository.save(clamped);
        return oldCumulative;
    }

    private static TeamDistanceProgress newProgress(UUID teamId) {
        TeamDistanceProgress progress = new TeamDistanceProgress();
        progress.setTeamId(teamId);
        return progress;
    }
}
