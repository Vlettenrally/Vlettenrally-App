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
        when(teamDistanceCalculator.recomputeFromScratch(team.getId())).thenReturn(42.0);

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
        verify(teamDistanceCalculator).recomputeFromScratch(team.getId());
        verify(automationEngine).onDistanceUpdate(eq(GAME_ID), eq(team.getId()), eq(42.0), eq(false));
    }

    @Test
    void persist_dropsPointRejectedByGuard() {
        when(locationIngestService.isValid(any())).thenReturn(true);
        when(locationIngestService.computeGate(any(), any(), any()))
            .thenReturn(new LocationIngestService.IngestGate(false, false));
        lenient().when(teamDistanceCalculator.recomputeFromScratch(team.getId())).thenReturn(10.0);

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
        when(teamDistanceCalculator.recomputeFromScratch(team.getId())).thenReturn(500.0);
        // Each consecutive pair reports one newly-credited pass.
        when(locationIngestService.detectCheckpointPasses(any(), any())).thenReturn(1);

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
}
