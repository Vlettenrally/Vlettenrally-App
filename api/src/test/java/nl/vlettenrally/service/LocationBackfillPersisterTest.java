package nl.vlettenrally.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;

import nl.vlettenrally.client.dto.Coordinates;
import nl.vlettenrally.client.dto.legendstracking.EventParticipantLocation;
import nl.vlettenrally.data.domain.Game;
import nl.vlettenrally.data.domain.LocationData;
import nl.vlettenrally.data.domain.Team;
import nl.vlettenrally.data.repository.mock.MockLocationDataRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.PrecisionModel;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * Unit tests for the transactional write side, {@link LocationBackfillPersister}. Uses the real
 * in-memory location repo (so dedupe reads and inserts are real) and a mocked
 * {@link LocationIngestService} so guard and checkpoint decisions are controlled. Calling
 * {@code persist} directly on a plain instance bypasses the {@code @Transactional} interceptor,
 * which is exactly what the mock-backed unit test wants.
 */
@ExtendWith(MockitoExtension.class)
class LocationBackfillPersisterTest {

    private static final GeometryFactory GEOMETRY_FACTORY = new GeometryFactory(new PrecisionModel(), 4326);
    private static final UUID GAME_ID = UUID.fromString("a1b2c3d4-e5f6-7890-abcd-ef1234567890");
    private static final Instant BASE = Instant.parse("2026-06-14T12:00:00Z");

    @Mock LocationIngestService locationIngestService;
    @Mock TeamDistanceCalculator teamDistanceCalculator;
    @Mock AutomationEngine automationEngine;

    private MockLocationDataRepository locationRepo;
    private LocationBackfillPersister persister;
    private Game game;
    private Team team;

    @BeforeEach
    void setUp() {
        locationRepo = new MockLocationDataRepository();

        persister = new LocationBackfillPersister();
        persister.locationDataRepository = locationRepo;
        persister.locationIngestService = locationIngestService;
        persister.teamDistanceCalculator = teamDistanceCalculator;
        persister.automationEngine = automationEngine;

        game = new Game();
        game.setId(GAME_ID);
        team = new Team();
        team.setTrackerId("20432");
    }

    private EventParticipantLocation providerPoint(double lon, double lat, int secondsFromBase) {
        Coordinates c = new Coordinates();
        c.setLongitude(lon);
        c.setLatitude(lat);
        EventParticipantLocation pl = new EventParticipantLocation();
        pl.setLocation(c);
        pl.setLocationTime(BASE.plus(secondsFromBase, ChronoUnit.SECONDS).toString());
        pl.setTrackerId(20432);
        return pl;
    }

    private void storeFix(double lon, double lat, int secondsFromBase) {
        LocationData ld = new LocationData();
        ld.setTeamId(team.getId());
        ld.setGameId(GAME_ID);
        ld.setLocation(GEOMETRY_FACTORY.createPoint(new Coordinate(lon, lat)));
        ld.setLocationTime(BASE.plus(secondsFromBase, ChronoUnit.SECONDS));
        locationRepo.save(ld);
    }

    @Test
    void persist_dedupesAndInsertsOnlyMissingPoints() {
        when(locationIngestService.isValid(any())).thenReturn(true);
        lenient().when(locationIngestService.passesGuards(any(), any(), any())).thenReturn(true);
        when(locationIngestService.computeGate(any(), any(), any()))
            .thenReturn(new LocationIngestService.IngestGate(false, false));
        when(teamDistanceCalculator.recomputeAfter(eq(team.getId()), any(Instant.class))).thenReturn(42.0);

        // Two fixes already stored (t=0 and t=300).
        storeFix(4.90, 52.3700, 0);
        storeFix(4.90, 52.3730, 300);

        // Provider returns the full track: the two dups plus two genuinely-missing gap points.
        List<EventParticipantLocation> track = List.of(
            providerPoint(4.90, 52.3700, 0),    // dup
            providerPoint(4.90, 52.3710, 100),  // new
            providerPoint(4.90, 52.3720, 200),  // new
            providerPoint(4.90, 52.3730, 300)   // dup
        );

        LocationBackfillService.BackfillResult result = persister.persist(game, team, track);

        assertEquals(LocationBackfillService.Status.COMPLETED, result.status());
        assertEquals(2, result.inserted());
        assertEquals(0, result.droppedByGuard());
        assertEquals(4, locationRepo.findByTeamId(team.getId()).size());
        // The rebuild boundary is the earliest *accepted* fix (t=100) — the deduped
        // t=0 candidate must not widen the recompute range.
        verify(teamDistanceCalculator).recomputeAfter(team.getId(), BASE.plus(100, ChronoUnit.SECONDS));
        verify(automationEngine).onDistanceUpdate(eq(GAME_ID), eq(team.getId()), eq(42.0), eq(false));
    }

    @Test
    void persist_dropsPointRejectedByGuard() {
        when(locationIngestService.isValid(any())).thenReturn(true);
        when(locationIngestService.computeGate(any(), any(), any()))
            .thenReturn(new LocationIngestService.IngestGate(false, false));
        lenient().when(teamDistanceCalculator.recomputeAfter(eq(team.getId()), any(Instant.class))).thenReturn(10.0);

        // A stored fix just before the gap makes the guard active for the first candidate.
        storeFix(4.90, 52.3700, 0);
        // First candidate accepted, second rejected (a spike).
        when(locationIngestService.passesGuards(any(), any(), any())).thenReturn(true, false);

        List<EventParticipantLocation> track = List.of(
            providerPoint(4.90, 52.3705, 60),
            providerPoint(9.99, 52.9999, 120)
        );

        LocationBackfillService.BackfillResult result = persister.persist(game, team, track);

        assertEquals(1, result.inserted());
        assertEquals(1, result.droppedByGuard());
    }

    @Test
    void persist_replaysCheckpointsWhenDetectionActive() {
        when(locationIngestService.isValid(any())).thenReturn(true);
        lenient().when(locationIngestService.passesGuards(any(), any(), any())).thenReturn(true);
        when(locationIngestService.computeGate(any(), any(), any()))
            .thenReturn(new LocationIngestService.IngestGate(true, true));
        when(teamDistanceCalculator.recomputeAfter(eq(team.getId()), any(Instant.class))).thenReturn(500.0);
        // Each consecutive pair reports one newly-credited pass.
        when(locationIngestService.detectCheckpointPasses(any(), any())).thenReturn(1);
        game.setStartTime(BASE.minus(1, ChronoUnit.HOURS));

        List<EventParticipantLocation> track = List.of(
            providerPoint(4.90, 52.3710, 100),
            providerPoint(4.90, 52.3720, 200)
        );

        LocationBackfillService.BackfillResult result = persister.persist(game, team, track);

        assertEquals(2, result.inserted());
        // Two accepted points with no boundary neighbours = one interior pair.
        assertEquals(1, result.checkpointPassesDetected());
        verify(locationIngestService, times(1)).detectCheckpointPasses(any(), any());
        verify(automationEngine).onDistanceUpdate(eq(GAME_ID), eq(team.getId()), eq(500.0), eq(true));
    }

    @Test
    void persist_excludesCheckpointPassesReachedBeforeStartTime() {
        when(locationIngestService.isValid(any())).thenReturn(true);
        lenient().when(locationIngestService.passesGuards(any(), any(), any())).thenReturn(true);
        when(locationIngestService.computeGate(any(), any(), any()))
            .thenReturn(new LocationIngestService.IngestGate(true, true));
        when(teamDistanceCalculator.recomputeAfter(eq(team.getId()), any(Instant.class))).thenReturn(500.0);
        when(locationIngestService.detectCheckpointPasses(any(), any())).thenReturn(1);
        // Backfill can replay the pre-start lead window; only pairs whose arrival is at/after the
        // start time may be credited.
        game.setStartTime(BASE.plus(150, ChronoUnit.SECONDS));

        List<EventParticipantLocation> track = List.of(
            providerPoint(4.90, 52.3700, 50),   // pre-start
            providerPoint(4.90, 52.3710, 100),  // pre-start
            providerPoint(4.90, 52.3720, 200)   // post-start
        );

        LocationBackfillService.BackfillResult result = persister.persist(game, team, track);

        assertEquals(3, result.inserted());
        // Only the (t=100 -> t=200) pair has an arrival at/after startTime; the (t=50 -> t=100)
        // pre-start pair is excluded even though checkpoint detection is otherwise active.
        assertEquals(1, result.checkpointPassesDetected());
        verify(locationIngestService, times(1)).detectCheckpointPasses(any(), any());
    }

    @Test
    void persist_stopsCreditingCheckpointsOnceTeamFinishesMidReplay() {
        when(locationIngestService.isValid(any())).thenReturn(true);
        lenient().when(locationIngestService.passesGuards(any(), any(), any())).thenReturn(true);
        when(locationIngestService.computeGate(any(), any(), any()))
            .thenReturn(new LocationIngestService.IngestGate(true, true));
        when(teamDistanceCalculator.recomputeAfter(eq(team.getId()), any(Instant.class))).thenReturn(500.0);
        // In the real pipeline, a CHECKPOINT_ENTRY automation rule can finish the team synchronously
        // from inside detectCheckpointPasses (AutomationEngine.onCheckpointPass -> FinishTeam action).
        // Simulate that side effect on the first segment.
        when(locationIngestService.detectCheckpointPasses(any(), any())).thenAnswer(invocation -> {
            team.setFinished(true);
            return 1;
        });
        game.setStartTime(BASE.minus(1, ChronoUnit.HOURS));

        List<EventParticipantLocation> track = List.of(
            providerPoint(4.90, 52.3710, 100),
            providerPoint(4.90, 52.3720, 200),
            providerPoint(4.90, 52.3730, 300)
        );

        LocationBackfillService.BackfillResult result = persister.persist(game, team, track);

        assertEquals(3, result.inserted());
        // Only the segment that triggers the finish is credited; later segments in the same batch
        // must not still be credited once the team has finished.
        assertEquals(1, result.checkpointPassesDetected());
        verify(locationIngestService, times(1)).detectCheckpointPasses(any(), any());
    }

    @Test
    void persist_creditsCheckpointsBeforeFinishedAtEvenWhenTeamAlreadyFinished() {
        when(locationIngestService.isValid(any())).thenReturn(true);
        lenient().when(locationIngestService.passesGuards(any(), any(), any())).thenReturn(true);
        when(locationIngestService.computeGate(any(), any(), any()))
            .thenReturn(new LocationIngestService.IngestGate(false, false));
        when(teamDistanceCalculator.recomputeAfter(eq(team.getId()), any(Instant.class))).thenReturn(500.0);
        when(locationIngestService.detectCheckpointPasses(any(), any())).thenReturn(1);
        game.setStartTime(BASE.minus(1, ChronoUnit.HOURS));
        // The team already finished (in an earlier, unrelated call) before this backfill even starts;
        // finishedAt sits between the second and third candidate.
        team.setFinished(true);
        team.setFinishedAt(BASE.plus(250, ChronoUnit.SECONDS));

        List<EventParticipantLocation> track = List.of(
            providerPoint(4.90, 52.3700, 100),  // before finishedAt
            providerPoint(4.90, 52.3710, 200),  // before finishedAt
            providerPoint(4.90, 52.3720, 300)   // at/after finishedAt
        );

        LocationBackfillService.BackfillResult result = persister.persist(game, team, track);

        assertEquals(3, result.inserted());
        // The (t=100 -> t=200) pair predates finishedAt and must still be creditable even though the
        // team is currently finished; the (t=200 -> t=300) pair, reached at/after finishedAt, must not.
        assertEquals(1, result.checkpointPassesDetected());
        verify(locationIngestService, times(1)).detectCheckpointPasses(any(), any());
    }

    @Test
    void persist_excludesCheckpointPassesAfterTailWindowCloses() {
        when(locationIngestService.isValid(any())).thenReturn(true);
        lenient().when(locationIngestService.passesGuards(any(), any(), any())).thenReturn(true);
        when(locationIngestService.computeGate(any(), any(), any()))
            .thenReturn(new LocationIngestService.IngestGate(false, false));
        when(teamDistanceCalculator.recomputeAfter(eq(team.getId()), any(Instant.class))).thenReturn(500.0);
        when(locationIngestService.detectCheckpointPasses(any(), any())).thenReturn(1);
        game.setStartTime(BASE.minus(1, ChronoUnit.HOURS));
        // Game ended at t=150s; default locationPollingTailMinutes=30 keeps the tail window open
        // until t=150s+30min=1950s.
        game.setEndTime(BASE.plus(150, ChronoUnit.SECONDS));

        List<EventParticipantLocation> track = List.of(
            providerPoint(4.90, 52.3700, 100),   // before end
            providerPoint(4.90, 52.3710, 200),   // after end, within tail window
            providerPoint(4.90, 52.3720, 2000)   // after end, past tail window
        );

        LocationBackfillService.BackfillResult result = persister.persist(game, team, track);

        assertEquals(3, result.inserted());
        // The (t=100 -> t=200) pair lands inside the tail window and is creditable; the
        // (t=200 -> t=2000) pair, reached long after the tail window closes, is not.
        assertEquals(1, result.checkpointPassesDetected());
        verify(locationIngestService, times(1)).detectCheckpointPasses(any(), any());
    }
}
