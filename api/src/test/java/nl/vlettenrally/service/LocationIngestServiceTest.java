package nl.vlettenrally.service;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.Point;
import org.locationtech.jts.geom.PrecisionModel;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import nl.vlettenrally.data.domain.Game;
import nl.vlettenrally.data.domain.LocationData;
import nl.vlettenrally.data.dto.game.TeamLocationResponse;
import nl.vlettenrally.data.repository.CheckpointRepository;
import nl.vlettenrally.data.repository.LocationDataRepository;
import nl.vlettenrally.websockets.MapSubscriptionManager;
import nl.vlettenrally.websockets.WebSocketPublisher;

import java.util.List;

/**
 * Unit tests for the ingestion guards in {@link LocationIngestService}.
 */
@ExtendWith(MockitoExtension.class)
class LocationIngestServiceTest {

    private static final GeometryFactory GEOMETRY_FACTORY = new GeometryFactory(new PrecisionModel(), 4326);

    @Mock
    LocationDataRepository locationDataRepository;

    @Mock
    WebSocketPublisher webSocketPublisher;

    @Mock
    MapSubscriptionManager mapSubscriptionManager;

    @Mock
    AutomationEngine automationEngine;

    @Mock
    TeamDistanceCalculator teamDistanceCalculator;

    @Mock
    GameService gameService;

    @Mock
    CheckpointRepository checkpointRepository;

    @Mock
    AchievementService achievementService;

    @InjectMocks
    LocationIngestService service;

    private UUID teamId;
    private UUID gameId;

    @BeforeEach
    void setUp() {
        service.minIntervalSeconds = 5;
        service.maxSpeedKmh = 120;
        service.boatMaxSpeedKmh = 25;
        service.turnAngleThresholdDegrees = 120;
        service.minBearingSegmentMeters = 10;
        service.maxAccuracyMeters = 50;
        service.staleFixSeconds = 300;
        teamId = UUID.randomUUID();
        gameId = UUID.randomUUID();
    }

    @Test
    void ingest_firstFix_isAccepted() {
        when(locationDataRepository.findRecentByTeamId(teamId, 2)).thenReturn(List.of());

        Optional<TeamLocationResponse> result = service.ingest(reading(52.0, 5.0, Instant.now()), "Team");

        assertTrue(result.isPresent());
        verify(locationDataRepository).save(any(LocationData.class));
        verify(teamDistanceCalculator).computeTraveledMeters(teamId);
    }

    @Test
    void ingest_tooSoonAfterLastFix_isDropped() {
        Instant base = Instant.now();
        when(locationDataRepository.findRecentByTeamId(teamId, 2))
            .thenReturn(List.of(reading(52.0, 5.0, base)));

        // 2 seconds after the last fix — under the 5s minimum interval.
        Optional<TeamLocationResponse> result = service.ingest(reading(52.0001, 5.0001, base.plusSeconds(2)), "Team");

        assertFalse(result.isPresent());
        verify(locationDataRepository, never()).save(any(LocationData.class));
    }

    @Test
    void ingest_implausibleJump_isDropped() {
        Instant base = Instant.now();
        when(locationDataRepository.findRecentByTeamId(teamId, 2))
            .thenReturn(List.of(reading(52.0, 5.0, base)));

        // ~0.7 km east in 10s ≈ 250 km/h, well over the 120 km/h ceiling.
        Optional<TeamLocationResponse> result = service.ingest(reading(52.0, 5.01, base.plusSeconds(10)), "Team");

        assertFalse(result.isPresent());
        verify(locationDataRepository, never()).save(any(LocationData.class));
    }

    @Test
    void ingest_plausibleMovement_isAccepted() {
        Instant base = Instant.now();
        when(locationDataRepository.findRecentByTeamId(teamId, 2))
            .thenReturn(List.of(reading(52.0, 5.0, base)));

        // ~0.7 km east in 60s ≈ 42 km/h — under the hard ceiling, and with no prior fix
        // there is no bearing to trigger the direction-gated boat-speed rule.
        Optional<TeamLocationResponse> result = service.ingest(reading(52.0, 5.01, base.plusSeconds(60)), "Team");

        assertTrue(result.isPresent());
        verify(locationDataRepository).save(any(LocationData.class));
    }

    @Test
    void ingest_outOfRangeCoordinates_isDropped() {
        Optional<TeamLocationResponse> result = service.ingest(reading(95.0, 5.0, Instant.now()), "Team");

        assertFalse(result.isPresent());
        verify(locationDataRepository, never()).save(any(LocationData.class));
    }

    @Test
    void ingest_recordsGeofencedPass_whenActiveAndInZone() {
        Instant time = Instant.now();
        UUID checkpointId = UUID.randomUUID();
        when(locationDataRepository.findRecentByTeamId(teamId, 2)).thenReturn(List.of());
        when(gameService.findById(gameId)).thenReturn(Optional.of(activeGame()));
        when(checkpointRepository.findContainingPoint(eq(gameId), any(Point.class)))
            .thenReturn(List.of(checkpointId));

        Optional<TeamLocationResponse> result = service.ingest(reading(52.0, 5.0, time), "Team");

        assertTrue(result.isPresent());
        verify(achievementService).recordVerifiedPass(teamId, checkpointId, time);
    }

    @Test
    void ingest_gameNotActive_skipsDetection() {
        when(locationDataRepository.findRecentByTeamId(teamId, 2)).thenReturn(List.of());
        Game completed = new Game();
        completed.setId(gameId);
        completed.setStatus(Game.GameStatus.COMPLETED);
        when(gameService.findById(gameId)).thenReturn(Optional.of(completed));

        Optional<TeamLocationResponse> result = service.ingest(reading(52.0, 5.0, Instant.now()), "Team");

        assertTrue(result.isPresent());
        verify(checkpointRepository, never()).findContainingPoint(any(), any());
        verify(achievementService, never()).recordVerifiedPass(any(), any(), any());
    }

    @Test
    void ingest_activeButOutsideAllZones_recordsNoPass() {
        when(locationDataRepository.findRecentByTeamId(teamId, 2)).thenReturn(List.of());
        when(gameService.findById(gameId)).thenReturn(Optional.of(activeGame()));
        when(checkpointRepository.findContainingPoint(eq(gameId), any(Point.class)))
            .thenReturn(List.of());

        service.ingest(reading(52.0, 5.0, Instant.now()), "Team");

        verify(achievementService, never()).recordVerifiedPass(any(), any(), any());
    }

    @Test
    void ingest_spikeAfterStraightRun_isDropped() {
        Instant base = Instant.now().minusSeconds(300);
        // Eastward run at boat speed: prev -> last establishes a ~east heading.
        LocationData prev = reading(52.0, 5.000, base);
        LocationData last = reading(52.0, 5.001, base.plusSeconds(60));
        when(locationDataRepository.findRecentByTeamId(teamId, 2)).thenReturn(List.of(last, prev));

        // Candidate sharply reverses ~690 m west in 60s (~41 km/h): turn 180°, boat-implausible.
        Optional<TeamLocationResponse> result = service.ingest(reading(52.0, 4.991, base.plusSeconds(120)), "Team");

        assertFalse(result.isPresent());
        verify(locationDataRepository, never()).save(any(LocationData.class));
    }

    @Test
    void ingest_genuineSharpTurnAtBoatSpeed_isAccepted() {
        Instant base = Instant.now().minusSeconds(300);
        LocationData prev = reading(52.0, 5.000, base);
        LocationData last = reading(52.0, 5.001, base.plusSeconds(60));
        when(locationDataRepository.findRecentByTeamId(teamId, 2)).thenReturn(List.of(last, prev));

        // A real U-turn: ~69 m back west in 60s (~4 km/h). Sharp angle but boat speed → keep it.
        Optional<TeamLocationResponse> result = service.ingest(reading(52.0, 5.000, base.plusSeconds(120)), "Team");

        assertTrue(result.isPresent());
        verify(locationDataRepository).save(any(LocationData.class));
    }

    @Test
    void ingest_poorAccuracy_isDropped() {
        Instant base = Instant.now().minusSeconds(120);
        when(locationDataRepository.findRecentByTeamId(teamId, 2))
            .thenReturn(List.of(reading(52.0, 5.0, base)));

        // 100 m reported accuracy, over the 50 m gate.
        Optional<TeamLocationResponse> result =
            service.ingest(reading(52.0001, 5.0001, base.plusSeconds(60), new BigDecimal("100.0")), "Team");

        assertFalse(result.isPresent());
        verify(locationDataRepository, never()).save(any(LocationData.class));
    }

    @Test
    void ingest_recordsPass_whenSegmentCrossesZoneBetweenFixes() {
        Instant base = Instant.now().minusSeconds(120);
        UUID checkpointId = UUID.randomUUID();
        when(locationDataRepository.findRecentByTeamId(teamId, 2))
            .thenReturn(List.of(reading(52.0, 5.000, base)));
        when(gameService.findById(gameId)).thenReturn(Optional.of(activeGame()));
        when(checkpointRepository.findCrossedBySegment(eq(gameId), any(Point.class), any(Point.class)))
            .thenReturn(List.of(checkpointId));

        // ~150 m east in 60s (~9 km/h). Neither endpoint need be in the zone; the segment is.
        Instant t = base.plusSeconds(60);
        Optional<TeamLocationResponse> result = service.ingest(reading(52.0, 5.0022, t), "Team");

        assertTrue(result.isPresent());
        verify(achievementService).recordVerifiedPass(teamId, checkpointId, t);
        verify(checkpointRepository, never()).findContainingPoint(any(), any());
    }

    @Test
    void ingest_staleGap_usesPointDetectionNotSegment() {
        Instant base = Instant.now().minusSeconds(700);
        when(locationDataRepository.findRecentByTeamId(teamId, 2))
            .thenReturn(List.of(reading(52.0, 5.000, base)));
        when(gameService.findById(gameId)).thenReturn(Optional.of(activeGame()));
        when(checkpointRepository.findContainingPoint(eq(gameId), any(Point.class)))
            .thenReturn(List.of());

        // 600s after the last fix — beyond the 300s staleness window, so no connecting segment.
        service.ingest(reading(52.0, 5.001, base.plusSeconds(600)), "Team");

        verify(checkpointRepository).findContainingPoint(eq(gameId), any(Point.class));
        verify(checkpointRepository, never()).findCrossedBySegment(any(), any(), any());
    }

    private Game activeGame() {
        Game game = new Game();
        game.setId(gameId);
        game.setStatus(Game.GameStatus.ACTIVE);
        return game;
    }

    private LocationData reading(double lat, double lon, Instant time) {
        return reading(lat, lon, time, null);
    }

    private LocationData reading(double lat, double lon, Instant time, BigDecimal accuracy) {
        Point point = GEOMETRY_FACTORY.createPoint(new Coordinate(lon, lat));
        LocationData data = new LocationData();
        data.setTeamId(teamId);
        data.setGameId(gameId);
        data.setLocation(point);
        data.setLocationTime(time);
        data.setAccuracy(accuracy);
        return data;
    }
}
