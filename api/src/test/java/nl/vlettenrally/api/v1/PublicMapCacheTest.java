package nl.vlettenrally.api.v1;

import nl.vlettenrally.data.domain.CircleCheckpoint;
import nl.vlettenrally.data.domain.Game;
import nl.vlettenrally.data.dto.game.PublicMapResponse;
import nl.vlettenrally.data.dto.game.PublicMapZoneResponse;
import nl.vlettenrally.data.dto.game.TeamLocationResponse;
import nl.vlettenrally.service.CheckpointService;
import nl.vlettenrally.service.GameService;
import nl.vlettenrally.service.LocationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link PublicMapCache}, the bean that assembles the public map payload. The
 * important guarantee here is that hidden zones never make it onto a public display.
 */
@ExtendWith(MockitoExtension.class)
class PublicMapCacheTest {

    private static final UUID GAME_ID = UUID.fromString("a1b2c3d4-e5f6-7890-abcd-ef1234567890");

    @Mock GameService gameService;
    @Mock LocationService locationService;
    @Mock CheckpointService checkpointService;

    @InjectMocks PublicMapCache cache;

    private Game game;

    @BeforeEach
    void setUp() {
        game = new Game();
        game.setId(GAME_ID);
        game.setName("Spring Rally");
        game.setStartTime(Instant.parse("2026-04-01T10:00:00Z"));
    }

    @Test
    void omitsHiddenZones() {
        when(gameService.findById(GAME_ID)).thenReturn(Optional.of(game));
        when(locationService.getLatestTeamLocations(GAME_ID)).thenReturn(Optional.of(List.of()));
        when(checkpointService.findByGameId(GAME_ID)).thenReturn(List.of(
            zone("Sluis", false),
            zone("Geheime zone", true)));

        PublicMapResponse body = cache.mapFor(GAME_ID).orElseThrow();

        assertEquals(List.of("Sluis"), body.zones().stream().map(PublicMapZoneResponse::name).toList());
    }

    @Test
    void returnsVisibleZonesAndTeams() {
        TeamLocationResponse position = new TeamLocationResponse(
            UUID.randomUUID(), "De Watervlooien", "Snelle vlettenvaarders",
            52.1326, 5.2913, 3.4, 90.0, "2026-04-01T11:00:00Z");
        when(gameService.findById(GAME_ID)).thenReturn(Optional.of(game));
        when(locationService.getLatestTeamLocations(GAME_ID)).thenReturn(Optional.of(List.of(position)));
        when(checkpointService.findByGameId(GAME_ID)).thenReturn(List.of(zone("Sluis", false)));

        PublicMapResponse body = cache.mapFor(GAME_ID).orElseThrow();

        assertEquals(GAME_ID, body.gameId());
        assertEquals("Spring Rally", body.gameName());
        assertEquals(1, body.teams().size());
        assertEquals("De Watervlooien", body.teams().getFirst().teamName());
        assertEquals(1, body.zones().size());
        assertEquals(75.0, body.zones().getFirst().radiusMeters());
        assertEquals("circle", body.zones().getFirst().zoneType());
    }

    @Test
    void allZonesHidden_yieldsEmptyZoneList() {
        when(gameService.findById(GAME_ID)).thenReturn(Optional.of(game));
        when(locationService.getLatestTeamLocations(GAME_ID)).thenReturn(Optional.of(List.of()));
        when(checkpointService.findByGameId(GAME_ID)).thenReturn(List.of(zone("Geheime zone", true)));

        assertTrue(cache.mapFor(GAME_ID).orElseThrow().zones().isEmpty());
    }

    @Test
    void missingLocations_yieldsEmptyTeamList() {
        // The location service reports "no such game" as an empty Optional; a display should still
        // get its zones rather than an error.
        when(gameService.findById(GAME_ID)).thenReturn(Optional.of(game));
        when(locationService.getLatestTeamLocations(GAME_ID)).thenReturn(Optional.empty());
        when(checkpointService.findByGameId(GAME_ID)).thenReturn(List.of(zone("Sluis", false)));

        PublicMapResponse body = cache.mapFor(GAME_ID).orElseThrow();

        assertTrue(body.teams().isEmpty());
        assertEquals(1, body.zones().size());
    }

    @Test
    void unknownGame_isEmpty() {
        when(gameService.findById(GAME_ID)).thenReturn(Optional.empty());

        assertFalse(cache.mapFor(GAME_ID).isPresent());
    }

    private CircleCheckpoint zone(String name, boolean hidden) {
        CircleCheckpoint cp = new CircleCheckpoint(75.0);
        cp.setId(UUID.randomUUID());
        cp.setGameId(GAME_ID);
        cp.setName(name);
        cp.setLatitude(52.1326);
        cp.setLongitude(5.2913);
        cp.setPoints(10);
        cp.setExternalId("ext-" + name);
        cp.setHidden(hidden);
        return cp;
    }
}
