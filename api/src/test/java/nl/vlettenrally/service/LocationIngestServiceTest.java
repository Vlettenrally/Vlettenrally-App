package nl.vlettenrally.service;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

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

import nl.vlettenrally.data.domain.LocationData;
import nl.vlettenrally.data.dto.game.TeamLocationResponse;
import nl.vlettenrally.data.repository.LocationDataRepository;
import nl.vlettenrally.websockets.MapSubscriptionManager;
import nl.vlettenrally.websockets.WebSocketPublisher;

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

    @InjectMocks
    LocationIngestService service;

    private UUID teamId;
    private UUID gameId;

    @BeforeEach
    void setUp() {
        service.minIntervalSeconds = 5;
        service.maxSpeedKmh = 120;
        teamId = UUID.randomUUID();
        gameId = UUID.randomUUID();
    }

    @Test
    void ingest_firstFix_isAccepted() {
        when(locationDataRepository.findLatestByTeamId(teamId)).thenReturn(Optional.empty());

        Optional<TeamLocationResponse> result = service.ingest(reading(52.0, 5.0, Instant.now()), "Team");

        assertTrue(result.isPresent());
        verify(locationDataRepository).save(any(LocationData.class));
        verify(teamDistanceCalculator).computeTraveledMeters(teamId);
    }

    @Test
    void ingest_tooSoonAfterLastFix_isDropped() {
        Instant base = Instant.now();
        when(locationDataRepository.findLatestByTeamId(teamId))
            .thenReturn(Optional.of(reading(52.0, 5.0, base)));

        // 2 seconds after the last fix — under the 5s minimum interval.
        Optional<TeamLocationResponse> result = service.ingest(reading(52.0001, 5.0001, base.plusSeconds(2)), "Team");

        assertFalse(result.isPresent());
        verify(locationDataRepository, never()).save(any(LocationData.class));
    }

    @Test
    void ingest_implausibleJump_isDropped() {
        Instant base = Instant.now();
        when(locationDataRepository.findLatestByTeamId(teamId))
            .thenReturn(Optional.of(reading(52.0, 5.0, base)));

        // ~0.7 km east in 10s ≈ 250 km/h, well over the 120 km/h ceiling.
        Optional<TeamLocationResponse> result = service.ingest(reading(52.0, 5.01, base.plusSeconds(10)), "Team");

        assertFalse(result.isPresent());
        verify(locationDataRepository, never()).save(any(LocationData.class));
    }

    @Test
    void ingest_plausibleMovement_isAccepted() {
        Instant base = Instant.now();
        when(locationDataRepository.findLatestByTeamId(teamId))
            .thenReturn(Optional.of(reading(52.0, 5.0, base)));

        // ~0.7 km east in 60s ≈ 42 km/h — under the ceiling.
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

    private LocationData reading(double lat, double lon, Instant time) {
        Point point = GEOMETRY_FACTORY.createPoint(new Coordinate(lon, lat));
        LocationData data = new LocationData();
        data.setTeamId(teamId);
        data.setGameId(gameId);
        data.setLocation(point);
        data.setLocationTime(time);
        return data;
    }
}
