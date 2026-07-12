package nl.vlettenrally.service;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import nl.vlettenrally.client.dto.legendstracking.EventParticipantLocation;
import nl.vlettenrally.data.domain.Game;
import nl.vlettenrally.data.domain.LocationData;
import nl.vlettenrally.data.domain.Team;
import nl.vlettenrally.data.repository.LocationDataRepository;
import nl.vlettenrally.service.LocationBackfillService.BackfillResult;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.Point;
import org.locationtech.jts.geom.PrecisionModel;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Transactional write side of {@link LocationBackfillService}. Kept as a separate bean so the
 * {@code @Transactional} interceptor actually fires (a self-invocation from the same bean would
 * bypass it) and so the expensive provider HTTP call in {@link LocationBackfillService} stays
 * outside the database transaction.
 * <p>
 * {@link #persist} inserts the previously-missing fixes in chronological order (running the outlier
 * guard with the true predecessor), rebuilds the distance-segment chain from the gap onward, and
 * replays checkpoint-pass detection over the gap — all atomically.
 */
@ApplicationScoped
public class LocationBackfillPersister {

    private static final GeometryFactory GEOMETRY_FACTORY = new GeometryFactory(new PrecisionModel(), 4326);

    /**
     * How far on each side of the gap to look for the boundary neighbour fixes used to seed the
     * outlier guard and to close the boundary checkpoint segments. Generous relative to the ~30s
     * polling cadence; if no neighbour exists within it, the boundary segment is simply skipped
     * (equivalent to a track re-acquiring after an outage).
     */
    private static final Duration BOUNDARY_LOOKAROUND = Duration.ofHours(1);

    @Inject
    LocationDataRepository locationDataRepository;

    @Inject
    LocationIngestService locationIngestService;

    @Inject
    TeamDistanceCalculator teamDistanceCalculator;

    @Inject
    AutomationEngine automationEngine;

    @Transactional
    public BackfillResult persist(Game game, Team team, List<EventParticipantLocation> providerPoints) {
        UUID gameId = game.getId();
        UUID teamId = team.getId();

        // Build valid candidates, chronological ascending. Points outside the game's time envelope
        // are dropped: the provider track spans the tracker's whole power-on period, and pre-start
        // (or, once the game has COMPLETED, post-tail-window) movement has no business entering
        // location_data. A second lower bound is the tracker-assignment moment: points recorded
        // before this team held the tracker belong to its previous holder and must not be imported
        // as this team's movement. This is the storage envelope; the accrual envelope is narrower
        // still — TeamDistanceCalculator only counts fixes inside [startTime, endTime], so stored
        // tail-window points never inflate the travelled distance either.
        Instant start = game.getStartTime();
        Instant assignedAt = team.getTrackerAssignedAt();
        Instant storageCeiling = completedTailWindowEnd(game);
        List<LocationData> candidates = new ArrayList<>();
        for (EventParticipantLocation pl : providerPoints) {
            if (pl.getLocation() == null) {
                continue;
            }
            Instant t = parseInstant(pl.getLocationTime());
            if (t == null) {
                continue;
            }
            if (start != null && t.isBefore(start)) {
                continue;
            }
            if (assignedAt != null && t.isBefore(assignedAt)) {
                continue;
            }
            if (storageCeiling != null && t.isAfter(storageCeiling)) {
                continue;
            }
            LocationData ld = toLocationData(gameId, teamId, pl, t);
            if (locationIngestService.isValid(ld)) {
                candidates.add(ld);
            }
        }
        if (candidates.isEmpty()) {
            return BackfillResult.completed(0, 0, 0);
        }
        candidates.sort(Comparator.comparing(LocationData::getLocationTime));

        Instant minT = candidates.getFirst().getLocationTime();
        Instant maxT = candidates.getLast().getLocationTime();

        // Dedupe against stored fixes in the covered range, normalised to whole seconds (provider
        // millisecond precision must not defeat the match).
        Set<Long> storedSeconds = new HashSet<>();
        for (LocationData stored : locationDataRepository.findByTeamIdAndTimeRange(teamId, minT, maxT)) {
            storedSeconds.add(stored.getLocationTime().getEpochSecond());
        }

        // Seed the outlier guard with the true chronological predecessors: the two stored fixes
        // immediately before the gap.
        List<LocationData> priorDesc = locationDataRepository
            .findByTeamIdAndTimeRange(teamId, minT.minus(BOUNDARY_LOOKAROUND), minT).stream()
            .filter(l -> l.getLocationTime().isBefore(minT))
            .sorted(Comparator.comparing(LocationData::getLocationTime).reversed())
            .limit(2)
            .toList();
        LocationData neighbourBefore = priorDesc.isEmpty() ? null : priorDesc.get(0);
        LocationData last = neighbourBefore;
        LocationData prev = priorDesc.size() > 1 ? priorDesc.get(1) : null;

        List<LocationData> accepted = new ArrayList<>();
        int dropped = 0;
        for (LocationData cand : candidates) {
            if (storedSeconds.contains(cand.getLocationTime().getEpochSecond())) {
                continue; // already stored
            }
            if (last != null && !locationIngestService.passesGuards(prev, last, cand)) {
                dropped++;
                continue;
            }
            locationDataRepository.save(cand);
            storedSeconds.add(cand.getLocationTime().getEpochSecond());
            accepted.add(cand);
            prev = last;
            last = cand;
        }

        if (accepted.isEmpty()) {
            return BackfillResult.completed(0, dropped, 0);
        }

        // Full pipeline: rebuild the distance-segment chain from the earliest inserted fix
        // onward, then replay checkpoints over the gap.
        LocationIngestService.IngestGate gate = locationIngestService.computeGate(game, teamId, maxT);
        double cumulative = teamDistanceCalculator.recomputeAfter(
            teamId, game, accepted.getFirst().getLocationTime());
        automationEngine.onDistanceUpdate(gameId, teamId, cumulative, gate.scoringActive());

        // Checkpoint eligibility is decided per segment inside replayCheckpoints, not by this single
        // gate.detectCheckpoints() snapshot: unlike the live poller (one fix, evaluated at its own
        // arrival time), a single backfill batch can span the game's end into its post-end tail
        // window and a team's actual finish instant, all in one call. (Pre-start / pre-assignment
        // points never get here — the candidate filter above drops them — though a stored pre-start
        // fix can still serve as the boundary neighbour opening the first segment.)
        int passes = replayCheckpoints(game, team, neighbourBefore, accepted, maxT);
        return BackfillResult.completed(accepted.size(), dropped, passes);
    }

    /**
     * Walk consecutive chronological pairs across {@code [neighbourBefore, ...accepted, neighbourAfter]}
     * and run the same segment-based checkpoint detection the live path uses, so a zone transited during
     * the gap (including on the boundary segments) is credited. {@code recordVerifiedPass} is idempotent,
     * so already-credited checkpoints are left untouched.
     * <p>
     * Each segment's own arrival time decides eligibility ({@link #isCheckpointEligible}) rather than
     * one upfront snapshot for the whole batch — a team that finished (or a game that ended) partway
     * through a replayed gap must still get credit for the legitimately-missed earlier segments, and a
     * team/game that hadn't started yet must not get credit just because it has started by the time
     * backfill runs.
     * <p>
     * On top of that, {@code team.isFinished()} is re-checked after every segment the walk was not
     * already finished at when this call started: a {@code CHECKPOINT_ENTRY} automation rule can finish
     * the team synchronously from inside {@code detectCheckpointPasses} itself (via
     * {@code AutomationEngine.onCheckpointPass}), stamping {@code finishedAt} with the wall-clock time of
     * this backfill run rather than the (historical) fix time it just replayed — so
     * {@link #isCheckpointEligible}'s {@code finishedAt} ceiling cannot catch that case, since every
     * remaining historical fix in the batch is still chronologically "before" that wall-clock stamp.
     * Only a live re-check catches a finish that happens mid-replay.
     */
    private int replayCheckpoints(Game game, Team team, LocationData neighbourBefore,
                                  List<LocationData> accepted, Instant maxT) {
        UUID teamId = team.getId();
        LocationData neighbourAfter = locationDataRepository
            .findByTeamIdAndTimeRange(teamId, maxT, maxT.plus(BOUNDARY_LOOKAROUND)).stream()
            .filter(l -> l.getLocationTime().isAfter(maxT))
            .min(Comparator.comparing(LocationData::getLocationTime))
            .orElse(null);

        List<LocationData> walk = new ArrayList<>();
        if (neighbourBefore != null) {
            walk.add(neighbourBefore);
        }
        walk.addAll(accepted);
        if (neighbourAfter != null) {
            walk.add(neighbourAfter);
        }

        boolean wasFinishedAtStart = team.isFinished();
        int passes = 0;
        for (int i = 1; i < walk.size(); i++) {
            if (!wasFinishedAtStart && team.isFinished()) {
                break;
            }
            LocationData reading = walk.get(i);
            if (!isCheckpointEligible(game, team, reading.getLocationTime())) {
                continue;
            }
            passes += locationIngestService.detectCheckpointPasses(walk.get(i - 1), reading);
        }
        return passes;
    }

    /**
     * Whether a fix at {@code t} could legitimately have been a checkpoint crossing: at/after the
     * game's start time, at/before its end time (or within the post-end tail window), and before the
     * team's recorded finish instant (if any). Mirrors {@link Game#isWithinPollingLeadWindow} /
     * {@link Game#isWithinPollingTailWindow}'s convention that a missing boundary means the window never
     * applies, so an unconfigured/never-started game never yields checkpoint credit.
     */
    private boolean isCheckpointEligible(Game game, Team team, Instant t) {
        Instant start = game.getStartTime();
        if (start == null || t.isBefore(start)) {
            return false;
        }
        Instant end = game.getEndTime();
        if (end != null && t.isAfter(end) && !game.isWithinPollingTailWindow(t)) {
            return false;
        }
        Instant finishedAt = team.getFinishedAt();
        return finishedAt == null || t.isBefore(finishedAt);
    }

    /**
     * Upper storage bound for backfilled fixes: once the game has COMPLETED, live polling stops
     * when the post-end tail window closes ({@link Game#isWithinPollingTailWindow}), so backfill
     * must not import past that point either. Null (no bound) while the game is still
     * PLANNED/ACTIVE — a game running past its planned end time keeps ingesting live, and
     * backfill must match — or when no end time is configured.
     */
    private static Instant completedTailWindowEnd(Game game) {
        if (game.getStatus() != Game.GameStatus.COMPLETED || game.getEndTime() == null) {
            return null;
        }
        Integer tail = game.getLocationPollingTailMinutes();
        long tailMinutes = tail != null && tail > 0 ? tail : 0;
        return game.getEndTime().plus(Duration.ofMinutes(tailMinutes));
    }

    private static LocationData toLocationData(UUID gameId, UUID teamId, EventParticipantLocation pl, Instant time) {
        Point point = GEOMETRY_FACTORY.createPoint(
            new Coordinate(pl.getLocation().getLongitude(), pl.getLocation().getLatitude()));
        LocationData ld = new LocationData();
        ld.setTeamId(teamId);
        ld.setGameId(gameId);
        ld.setLocation(point);
        ld.setLocationTime(time);
        ld.setAccuracy(pl.getGpsAccuracy() != null && pl.getGpsAccuracy() > 0
            ? BigDecimal.valueOf(pl.getGpsAccuracy()) : null);
        ld.setSpeed(pl.getSpeed() != null ? BigDecimal.valueOf(pl.getSpeed()) : null);
        ld.setHeading(null);
        return ld;
    }

    /** Parse the provider timestamp, returning null (drop the point) rather than substituting now(). */
    private static Instant parseInstant(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        try {
            return Instant.parse(raw);
        } catch (Exception e) {
            return null;
        }
    }
}
