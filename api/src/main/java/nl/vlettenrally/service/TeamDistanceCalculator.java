package nl.vlettenrally.service;

import io.quarkus.logging.Log;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import nl.vlettenrally.data.domain.Game;
import nl.vlettenrally.data.domain.LocationData;
import nl.vlettenrally.data.domain.PauseWindows;
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
 * Only fixes inside the game's playing window {@code [startTime, endTime]} (inclusive,
 * a null bound is unbounded on that side) count: fixes from the pre-start polling lead
 * window, the post-end tail window, or a game running late past its planned end are
 * stored in {@code location_data} but never seed the chain nor advance the anchor. The
 * window is derived purely from timestamps, never from the game's (mutable) status, so
 * a live accrual and a later rebuild always agree.
 * <p>
 * An emergency stop punches holes in that window: fixes inside a
 * {@link nl.vlettenrally.data.domain.PauseWindows pause window} are skipped exactly like
 * out-of-window ones, and the first counted fix after a stop chains from the anchor with
 * {@code meters = 0} — a <em>bridge</em> — so travel across the frozen stretch earns
 * nothing. Recording pauses as timestamped periods rather than reading the game's status
 * is what keeps the invariant above true: a status flag tells a rebuild nothing about
 * which meters were travelled while frozen. Because the holes come from the same
 * timestamp snapshot, a game may be paused and resumed any number of times without the
 * live total and a rebuilt total drifting apart.
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

    @Inject
    GamePauseWindowCache pauseWindowCache;

    @ConfigProperty(name = "vlettenrally.distance.min-segment-meters", defaultValue = "8")
    double minSegmentMeters;

    /**
     * Fold one just-stored live fix into the team's segment chain and return the
     * updated total travelled distance in metres. The fix is passed in-hand, so the
     * live path never re-reads {@code location_data}.
     */
    public double accrueFix(UUID teamId, Game game, LocationData fix) {
        segmentRepository.lockTeam(teamId);
        appendFix(teamId, game, pauseWindows(game),
                segmentRepository.findLastByTeamId(teamId).orElse(null), fix);
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
     * is not guaranteed); {@link AutomationEngine#onDistanceUpdate} treats its baseline
     * as a high-water mark — a decrease neither credits nor lowers the baseline, so the
     * climb-back is not re-credited and no clamp is needed here.
     * <p>
     * An admin editing the game's start/end time after fixes were accrued (or skipped)
     * is not reconciled here on its own: segments already written past a shortened end
     * remain, and fixes skipped past an extended end stay uncounted, until a backfill
     * of that range triggers this rebuild.
     */
    public double recomputeAfter(UUID teamId, Game game, Instant earliestChangedTime) {
        segmentRepository.lockTeam(teamId);
        segmentRepository.deleteFromTime(teamId, earliestChangedTime);

        // One snapshot for the whole walk: a resume landing mid-rebuild would otherwise
        // have the early fixes see a different set of holes than the later ones, producing
        // a chain neither live accrual nor the next rebuild reproduces.
        PauseWindows pauses = pauseWindows(game);
        TeamDistanceSegment anchor = segmentRepository.findLastByTeamId(teamId).orElse(null);
        Instant after = anchor != null ? anchor.getToTime() : Instant.EPOCH;
        for (LocationData fix : locationDataRepository.findByTeamIdAfter(teamId, after)) {
            anchor = appendFix(teamId, game, pauses, anchor, fix);
        }
        return segmentRepository.sumMetersByTeamId(teamId);
    }

    /**
     * Apply the accrual rule for one fix against the current anchor (null when the
     * chain is empty): seed the chain on the first fix, append a segment once the fix
     * is at least {@code minSegmentMeters} from the anchor, or hold the anchor on
     * jitter. Returns the (possibly advanced) anchor.
     * <p>
     * A fix outside the game window is skipped outright, so the chain seeds at the
     * first in-window fix and the hop from a pre-start position to it is not credited.
     * That first-counted-fix-seeds rule applies only to an empty chain: a migrated
     * team's carryover seed is already the anchor, so its first in-window fix chains
     * from the carryover endpoint and the pre-start gap is credited (pre-existing
     * legacy behaviour, accepted).
     * <p>
     * A fix separated from the anchor by an emergency stop gets a <em>bridge</em>: a
     * zero-metre segment moving the anchor to the new position. It cannot be a second
     * seed row — V69's {@code uq_tds_from UNIQUE NULLS NOT DISTINCT (team_id, from_time)}
     * allows only one null-from_time row per team — and it must be written even when the
     * hop is below the jitter threshold, or the anchor would linger at the pre-pause
     * position and credit that hop to the fix after it.
     */
    private TeamDistanceSegment appendFix(UUID teamId, Game game, PauseWindows pauses,
                                          TeamDistanceSegment anchor, LocationData fix) {
        if (!countsForDistance(game, pauses, fix.getLocationTime())) {
            // Lead/tail-window fix, overtime past a strict end, or a fix from during an
            // emergency stop: stored, never counted. Deliberately quiet — pre-start polling
            // skips one fix per team per cycle.
            return anchor;
        }

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

        boolean bridgesPause = pauses.separates(anchor.getToTime(), fix.getLocationTime());
        double d = GeoDistance.haversineMeters(anchor.getToLon(), anchor.getToLat(), p.getX(), p.getY());
        if (d < minSegmentMeters && !bridgesPause) {
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
        segment.setMeters(bridgesPause ? 0 : d);
        return segmentRepository.save(segment);
    }

    /**
     * The game's emergency-stop windows, snapshotted once per operation. A null game is
     * defensive only — the fix's game FK guarantees the row exists.
     */
    private PauseWindows pauseWindows(Game game) {
        return game == null ? PauseWindows.none() : pauseWindowCache.windowsFor(game.getId());
    }

    /**
     * Whether a fix at {@code t} falls inside the game's playing window
     * {@code [startTime, endTime]}, inclusive on both ends, and outside every emergency
     * stop. A null bound is unbounded on that side (mirroring
     * {@link Game#isWithinPollingLeadWindow}'s convention; a game without an end time
     * never auto-completes and keeps accruing). A null game is unbounded too — defensive
     * only, the fix's game FK guarantees the row exists.
     */
    private static boolean countsForDistance(Game game, PauseWindows pauses, Instant t) {
        if (t == null) {
            // Ingest validation rejects these; skip defensively like a null location.
            return false;
        }
        if (pauses.contains(t)) {
            // Travelled while the game was frozen: stored and broadcast, never counted.
            return false;
        }
        if (game == null) {
            return true;
        }
        Instant start = game.getStartTime();
        if (start != null && t.isBefore(start)) {
            return false;
        }
        Instant end = game.getEndTime();
        return end == null || !t.isAfter(end);
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
