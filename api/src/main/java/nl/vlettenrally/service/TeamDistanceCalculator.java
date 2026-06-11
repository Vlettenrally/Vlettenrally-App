package nl.vlettenrally.service;

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

    private static TeamDistanceProgress newProgress(UUID teamId) {
        TeamDistanceProgress progress = new TeamDistanceProgress();
        progress.setTeamId(teamId);
        return progress;
    }
}
