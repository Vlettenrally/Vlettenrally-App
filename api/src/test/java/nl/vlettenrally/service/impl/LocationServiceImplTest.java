package nl.vlettenrally.service.impl;

import nl.vlettenrally.data.domain.Game;
import nl.vlettenrally.data.domain.LocationData;
import nl.vlettenrally.data.domain.Team;
import nl.vlettenrally.data.dto.game.TeamLocationResponse;
import nl.vlettenrally.data.repository.LocationDataRepository;
import nl.vlettenrally.data.repository.TeamRepository;
import nl.vlettenrally.service.GameService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.Point;
import org.locationtech.jts.geom.PrecisionModel;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class LocationServiceImplTest {

    private static final GeometryFactory GEOMETRY_FACTORY = new GeometryFactory(new PrecisionModel(), 4326);

    @Mock
    LocationDataRepository locationDataRepository;

    @Mock
    TeamRepository teamRepository;

    @Mock
    GameService gameService;

    @InjectMocks
    LocationServiceImpl locationService;

    private UUID gameId;
    private UUID teamId;
    private Team team;

    @BeforeEach
    void setUp() {
        gameId = UUID.randomUUID();
        teamId = UUID.randomUUID();
        team = new Team(teamId, gameId, "De Watervlooien", "A test team");
    }

    // ==================== getLatestTeamLocations ====================

    @Test
    void getLatestTeamLocations_gameNotFound_returnsEmpty() {
        when(gameService.findById(gameId)).thenReturn(Optional.empty());

        Optional<List<TeamLocationResponse>> result = locationService.getLatestTeamLocations(gameId);

        assertTrue(result.isEmpty());
        verify(teamRepository, never()).findByGameId(any());
        verify(locationDataRepository, never()).findLatestByGameId(any());
    }

    @Test
    void getLatestTeamLocations_mapsCoordinatesAndSkipsTeamsWithoutLocation() {
        Team second = new Team(UUID.randomUUID(), gameId, "Windkracht Tien", null);
        Point pt = GEOMETRY_FACTORY.createPoint(new Coordinate(4.9, 52.37));
        LocationData loc = new LocationData(
            UUID.randomUUID(), team.getId(), gameId, pt,
            Instant.now(), BigDecimal.valueOf(5), null,
            BigDecimal.valueOf(12.5), BigDecimal.valueOf(180));

        when(gameService.findById(gameId)).thenReturn(Optional.of(new Game()));
        when(teamRepository.findByGameId(gameId)).thenReturn(List.of(team, second));
        // The batch query omits teams without a stored fix, so `second` simply is not in the result.
        when(locationDataRepository.findLatestByGameId(gameId)).thenReturn(List.of(loc));

        List<TeamLocationResponse> result = locationService.getLatestTeamLocations(gameId).orElseThrow();

        assertEquals(1, result.size());
        TeamLocationResponse response = result.getFirst();
        assertEquals(team.getId(), response.teamId());
        assertEquals("De Watervlooien", response.teamName());
        assertEquals(52.37, response.latitude(), 1e-9);
        assertEquals(4.9, response.longitude(), 1e-9);
        assertEquals(12.5, response.speed(), 1e-9);
        assertEquals(180.0, response.heading(), 1e-9);
        assertNotNull(response.locationTime());
    }

    @Test
    void getLatestTeamLocations_issuesOneQueryForTheWholeGame() {
        Team second = new Team(UUID.randomUUID(), gameId, "Windkracht Tien", null);
        Point pt = GEOMETRY_FACTORY.createPoint(new Coordinate(4.9, 52.37));
        LocationData first = new LocationData(
            UUID.randomUUID(), team.getId(), gameId, pt,
            Instant.now(), null, null, null, null);
        LocationData other = new LocationData(
            UUID.randomUUID(), second.getId(), gameId, pt,
            Instant.now(), null, null, null, null);

        when(gameService.findById(gameId)).thenReturn(Optional.of(new Game()));
        when(teamRepository.findByGameId(gameId)).thenReturn(List.of(team, second));
        when(locationDataRepository.findLatestByGameId(gameId)).thenReturn(List.of(first, other));

        assertEquals(2, locationService.getLatestTeamLocations(gameId).orElseThrow().size());

        // Guards the fix for the admin map: a per-team fan-out here cost one round trip per team.
        verify(locationDataRepository, times(1)).findLatestByGameId(gameId);
        verify(locationDataRepository, never()).findLatestByTeamId(any());
    }

    @Test
    void getLatestTeamLocations_skipsRowsForTeamsNotInTheGame() {
        Point pt = GEOMETRY_FACTORY.createPoint(new Coordinate(4.9, 52.37));
        LocationData stray = new LocationData(
            UUID.randomUUID(), UUID.randomUUID(), gameId, pt,
            Instant.now(), null, null, null, null);

        when(gameService.findById(gameId)).thenReturn(Optional.of(new Game()));
        when(teamRepository.findByGameId(gameId)).thenReturn(List.of(team));
        when(locationDataRepository.findLatestByGameId(gameId)).thenReturn(List.of(stray));

        assertTrue(locationService.getLatestTeamLocations(gameId).orElseThrow().isEmpty());
    }

    // ==================== getTeamLocationHistory ====================

    @Test
    void getTeamLocationHistory_gameNotFound_returnsEmpty() {
        when(gameService.findById(gameId)).thenReturn(Optional.empty());

        Optional<List<TeamLocationResponse>> result = locationService.getTeamLocationHistory(gameId, teamId, 60);

        assertTrue(result.isEmpty());
        verify(teamRepository, never()).findByGameId(any());
        verify(locationDataRepository, never()).findByTeamIdAndTimeRange(any(), any(), any());
    }

    @Test
    void getTeamLocationHistory_teamNotInGame_returnsEmpty() {
        when(gameService.findById(gameId)).thenReturn(Optional.of(new Game()));
        when(teamRepository.findByGameId(gameId)).thenReturn(List.of(team));

        Optional<List<TeamLocationResponse>> result =
            locationService.getTeamLocationHistory(gameId, UUID.randomUUID(), 60);

        assertTrue(result.isEmpty());
        verify(locationDataRepository, never()).findByTeamIdAndTimeRange(any(), any(), any());
    }

    @Test
    void getTeamLocationHistory_returnsPointsSortedAscendingByTime() {
        Instant older = Instant.now().minus(10, ChronoUnit.MINUTES);
        Instant newer = Instant.now().minus(2, ChronoUnit.MINUTES);
        LocationData a = locationAt(newer, 4.9, 52.37);
        LocationData b = locationAt(older, 4.8, 52.36);

        when(gameService.findById(gameId)).thenReturn(Optional.of(new Game()));
        when(teamRepository.findByGameId(gameId)).thenReturn(List.of(team));
        // Repo returns descending (as JPA/mock do); service must reverse it.
        when(locationDataRepository.findByTeamIdAndTimeRange(eq(teamId), any(), any()))
            .thenReturn(List.of(a, b));

        List<TeamLocationResponse> result =
            locationService.getTeamLocationHistory(gameId, teamId, 60).orElseThrow();

        assertEquals(2, result.size());
        assertEquals(older.toString(), result.get(0).locationTime());
        assertEquals(newer.toString(), result.get(1).locationTime());
    }

    @Test
    void getTeamLocationHistory_clampsAboveCeiling() {
        when(gameService.findById(gameId)).thenReturn(Optional.of(new Game()));
        when(teamRepository.findByGameId(gameId)).thenReturn(List.of(team));
        when(locationDataRepository.findByTeamIdAndTimeRange(eq(teamId), any(), any())).thenReturn(List.of());

        locationService.getTeamLocationHistory(gameId, teamId, 9999);

        ArgumentCaptor<Instant> startCaptor = ArgumentCaptor.forClass(Instant.class);
        ArgumentCaptor<Instant> endCaptor = ArgumentCaptor.forClass(Instant.class);
        verify(locationDataRepository).findByTeamIdAndTimeRange(eq(teamId), startCaptor.capture(), endCaptor.capture());

        long windowSeconds = endCaptor.getValue().getEpochSecond() - startCaptor.getValue().getEpochSecond();
        assertEquals(LocationServiceImpl.MAX_HISTORY_WINDOW_MINUTES * 60L, windowSeconds);
    }

    @Test
    void getTeamLocationHistory_allTimeSentinel_queriesFromEpoch() {
        when(gameService.findById(gameId)).thenReturn(Optional.of(new Game()));
        when(teamRepository.findByGameId(gameId)).thenReturn(List.of(team));
        when(locationDataRepository.findByTeamIdAndTimeRange(eq(teamId), any(), any())).thenReturn(List.of());

        // windowMinutes <= 0 is the "all time" sentinel.
        locationService.getTeamLocationHistory(gameId, teamId, 0);

        ArgumentCaptor<Instant> startCaptor = ArgumentCaptor.forClass(Instant.class);
        verify(locationDataRepository).findByTeamIdAndTimeRange(eq(teamId), startCaptor.capture(), any());

        assertEquals(Instant.EPOCH, startCaptor.getValue());
    }

    @Test
    void getTeamLocationHistory_emptyHistory_returnsEmptyList() {
        when(gameService.findById(gameId)).thenReturn(Optional.of(new Game()));
        when(teamRepository.findByGameId(gameId)).thenReturn(List.of(team));
        when(locationDataRepository.findByTeamIdAndTimeRange(eq(teamId), any(), any())).thenReturn(List.of());

        List<TeamLocationResponse> result =
            locationService.getTeamLocationHistory(gameId, teamId, 60).orElseThrow();

        assertTrue(result.isEmpty());
    }

    @Test
    void getTeamLocationHistory_nullSpeedAndHeading_mappedToNull() {
        LocationData loc = locationAt(Instant.now(), 4.9, 52.37);
        loc.setSpeed(null);
        loc.setHeading(null);

        when(gameService.findById(gameId)).thenReturn(Optional.of(new Game()));
        when(teamRepository.findByGameId(gameId)).thenReturn(List.of(team));
        when(locationDataRepository.findByTeamIdAndTimeRange(eq(teamId), any(), any()))
            .thenReturn(List.of(loc));

        TeamLocationResponse response = locationService.getTeamLocationHistory(gameId, teamId, 30)
            .orElseThrow().getFirst();

        assertNull(response.speed());
        assertNull(response.heading());
        assertFalse(response.locationTime().isEmpty());
    }

    private LocationData locationAt(Instant t, double lng, double lat) {
        Point pt = GEOMETRY_FACTORY.createPoint(new Coordinate(lng, lat));
        return new LocationData(
            UUID.randomUUID(), team.getId(), gameId, pt, t,
            BigDecimal.valueOf(5), null,
            BigDecimal.valueOf(10), BigDecimal.valueOf(90));
    }
}
