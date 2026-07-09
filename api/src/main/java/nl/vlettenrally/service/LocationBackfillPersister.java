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
 * guard with the true predecessor), recomputes travelled distance once, and replays checkpoint-pass
 * detection over the gap — all atomically.
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

        // Build valid candidates, chronological ascending.
        List<LocationData> candidates = new ArrayList<>();
        for (EventParticipantLocation pl : providerPoints) {
            if (pl.getLocation() == null) {
                continue;
            }
            Instant t = parseInstant(pl.getLocationTime());
            if (t == null) {
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

        // Full pipeline: recompute distance once (monotonic), then replay checkpoints over the gap.
        LocationIngestService.IngestGate gate = locationIngestService.computeGate(gameId, teamId, maxT);
        double cumulative = teamDistanceCalculator.recomputeFromScratch(teamId);
        automationEngine.onDistanceUpdate(gameId, teamId, cumulative, gate.scoringActive());

        int passes = 0;
        if (gate.detectCheckpoints()) {
            passes = replayCheckpoints(teamId, neighbourBefore, accepted, maxT);
        }
        return BackfillResult.completed(accepted.size(), dropped, passes);
    }

    /**
     * Walk consecutive chronological pairs across {@code [neighbourBefore, ...accepted, neighbourAfter]}
     * and run the same segment-based checkpoint detection the live path uses, so a zone transited during
     * the gap (including on the boundary segments) is credited. {@code recordVerifiedPass} is idempotent,
     * so already-credited checkpoints are left untouched.
     */
    private int replayCheckpoints(UUID teamId, LocationData neighbourBefore,
                                  List<LocationData> accepted, Instant maxT) {
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

        int passes = 0;
        for (int i = 1; i < walk.size(); i++) {
            passes += locationIngestService.detectCheckpointPasses(walk.get(i - 1), walk.get(i));
        }
        return passes;
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
