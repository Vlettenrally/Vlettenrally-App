package nl.vlettenrally.service.impl;

import nl.vlettenrally.data.domain.Checkpoint;
import nl.vlettenrally.data.domain.CircleCheckpoint;
import nl.vlettenrally.data.domain.PolygonCheckpoint;
import nl.vlettenrally.data.dto.game.CheckpointRequest;
import nl.vlettenrally.data.repository.CheckpointRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.locationtech.jts.geom.Coordinate;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for checkpoint create/update logic, in particular the zone type
 * switching (circle ↔ polygon) that preserves common fields.
 */
@ExtendWith(MockitoExtension.class)
class CheckpointServiceImplTest {

    private static final List<List<Double>> TRIANGLE = List.of(
        List.of(52.0, 4.0),
        List.of(52.1, 4.0),
        List.of(52.1, 4.1)
    );

    @Mock CheckpointRepository checkpointRepository;

    @InjectMocks CheckpointServiceImpl service;

    private UUID checkpointId;
    private UUID gameId;

    @BeforeEach
    void setUp() {
        checkpointId = UUID.randomUUID();
        gameId = UUID.randomUUID();
    }

    private CheckpointRequest request(String name, String zoneType, Double radiusMeters, List<List<Double>> polygonPoints) {
        return new CheckpointRequest(name, "2026-06-10T08:00:00Z", "2026-06-10T18:00:00Z",
            25, 52.05, 4.05, zoneType, radiusMeters, polygonPoints);
    }

    private CircleCheckpoint circleCheckpoint() {
        CircleCheckpoint circle = new CircleCheckpoint(100);
        circle.setId(checkpointId);
        circle.setGameId(gameId);
        circle.setName("Sluis");
        circle.setPoints(10);
        circle.setExternalId("ext-1");
        circle.setCreatedAt(Instant.parse("2026-06-01T00:00:00Z"));
        return circle;
    }

    @Test
    void updateSwitchesCircleToPolygonPreservingCommonFields() {
        CircleCheckpoint existing = circleCheckpoint();
        when(checkpointRepository.findById(checkpointId)).thenReturn(Optional.of(existing));
        when(checkpointRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        Optional<Checkpoint> result = service.update(checkpointId, request("Brug", "polygon", null, TRIANGLE));

        PolygonCheckpoint poly = assertInstanceOf(PolygonCheckpoint.class, result.orElseThrow());
        assertEquals(checkpointId, poly.getId());
        assertEquals(gameId, poly.getGameId());
        assertEquals("ext-1", poly.getExternalId());
        assertEquals(Instant.parse("2026-06-01T00:00:00Z"), poly.getCreatedAt());
        assertEquals("Brug", poly.getName());
        assertEquals(25, poly.getPoints());
        // Ring is closed: 3 vertices + repeated first point
        Coordinate[] ring = poly.getZonePolygon().getExteriorRing().getCoordinates();
        assertEquals(4, ring.length);
        assertEquals(4.0, ring[0].getX()); // lng
        assertEquals(52.0, ring[0].getY()); // lat
    }

    @Test
    void updateSwitchesPolygonToCircle() {
        PolygonCheckpoint existing = new PolygonCheckpoint();
        existing.setId(checkpointId);
        existing.setGameId(gameId);
        existing.setName("Sluis");
        when(checkpointRepository.findById(checkpointId)).thenReturn(Optional.of(existing));
        when(checkpointRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        Optional<Checkpoint> result = service.update(checkpointId, request(null, "circle", 150.0, null));

        CircleCheckpoint circle = assertInstanceOf(CircleCheckpoint.class, result.orElseThrow());
        assertEquals(checkpointId, circle.getId());
        assertEquals(150.0, circle.getRadiusMeters());
        // Name in the request was null, so the existing name is kept
        assertEquals("Sluis", circle.getName());
    }

    @Test
    void updateSameTypeUpdatesInPlace() {
        CircleCheckpoint existing = circleCheckpoint();
        when(checkpointRepository.findById(checkpointId)).thenReturn(Optional.of(existing));
        when(checkpointRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        Optional<Checkpoint> result = service.update(checkpointId, request(null, "circle", 75.0, null));

        assertSame(existing, result.orElseThrow());
        assertEquals(75.0, existing.getRadiusMeters());
        assertEquals("Sluis", existing.getName());
        assertEquals(Instant.parse("2026-06-10T08:00:00Z"), existing.getOpeningTime());
    }

    @Test
    void updateReturnsEmptyForUnknownId() {
        when(checkpointRepository.findById(checkpointId)).thenReturn(Optional.empty());

        Optional<Checkpoint> result = service.update(checkpointId, request("Brug", "circle", 50.0, null));

        assertTrue(result.isEmpty());
        verify(checkpointRepository, never()).save(any());
    }

    @Test
    void createBuildsPolygonCheckpoint() {
        when(checkpointRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        Checkpoint created = service.create(gameId, request("Brug", "polygon", null, TRIANGLE));

        PolygonCheckpoint poly = assertInstanceOf(PolygonCheckpoint.class, created);
        assertEquals(gameId, poly.getGameId());
        assertEquals("Brug", poly.getName());
        assertEquals(25, poly.getPoints());
        assertEquals(4, poly.getZonePolygon().getExteriorRing().getCoordinates().length);
    }

    @Test
    void createBuildsCircleCheckpointByDefault() {
        when(checkpointRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        Checkpoint created = service.create(gameId, request("Brug", null, 200.0, null));

        CircleCheckpoint circle = assertInstanceOf(CircleCheckpoint.class, created);
        assertEquals(gameId, circle.getGameId());
        assertEquals(200.0, circle.getRadiusMeters());
        assertEquals(Instant.parse("2026-06-10T18:00:00Z"), circle.getClosingTime());
    }
}
