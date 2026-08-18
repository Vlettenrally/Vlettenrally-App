package nl.vlettenrally.service;

import nl.vlettenrally.client.LocationApiClient;
import nl.vlettenrally.client.dto.Coordinates;
import nl.vlettenrally.client.dto.legendstracking.EventParticipantLocation;
import nl.vlettenrally.client.dto.legendstracking.EventParticipantLocationsBody;
import nl.vlettenrally.client.dto.legendstracking.EventParticipantsLocationsResponse;
import nl.vlettenrally.client.exception.LocationApiException;
import nl.vlettenrally.data.domain.Game;
import nl.vlettenrally.data.domain.LocationData;
import nl.vlettenrally.data.domain.Team;
import nl.vlettenrally.data.repository.LocationDataRepository;
import nl.vlettenrally.data.repository.TeamRepository;
import nl.vlettenrally.observability.LocationMetrics;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Covers the per-sweep query shape of {@link LocationPollingScheduler}.
 * <p>
 * The sweep used to read every team's last stored fix one row at a time, which at full field
 * size meant hundreds of DB round trips every polling interval. These tests pin the batched
 * shape so the fan-out cannot creep back in.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class LocationPollingSchedulerTest {

    @Mock
    GameService gameService;

    @Mock
    TeamRepository teamRepository;

    @Mock
    LocationApiClient locationApiClient;

    @Mock
    LocationIngestService locationIngestService;

    @Mock
    LocationDataRepository locationDataRepository;

    @Mock
    LocationBackfillService locationBackfillService;

    @Mock
    BackfillThrottle backfillThrottle;

    @Mock
    PollingLeader pollingLeader;

    @Mock
    LocationMetrics metrics;

    @Mock
    TrackerStatusRecorder trackerStatusRecorder;

    @InjectMocks
    LocationPollingScheduler scheduler;

    private UUID gameId;
    private Game game;

    @BeforeEach
    void setUp() {
        scheduler.backfillGapThreshold = Duration.ofMinutes(15);

        gameId = UUID.randomUUID();
        game = new Game();
        game.setId(gameId);
        game.setStatus(Game.GameStatus.ACTIVE);
        game.setLocationEndpointActive(true);
        game.setLocationEndpointUrl("https://tracking.example/api/event/1");
        game.setLocationPollingFrequency(1);

        when(pollingLeader.tryAcquireOrRenew()).thenReturn(true);
        when(gameService.findByStatus(Game.GameStatus.ACTIVE)).thenReturn(List.of(game));
        when(gameService.findByStatus(Game.GameStatus.PAUSED)).thenReturn(List.of());
        when(gameService.findByStatus(Game.GameStatus.PLANNED)).thenReturn(List.of());
        when(gameService.findByStatus(Game.GameStatus.COMPLETED)).thenReturn(List.of());
        when(backfillThrottle.lockedTeams(anyCollection())).thenReturn(Set.of());
        when(locationIngestService.ingest(any(), any(), any(), any())).thenReturn(Optional.empty());
    }

    private Team teamWithTracker(String trackerId) {
        Team team = new Team(UUID.randomUUID(), gameId, "Team " + trackerId, null);
        team.setTrackerId(trackerId);
        return team;
    }

    private void feed(List<Team> teams, Instant fixTime) throws LocationApiException {
        when(teamRepository.findByGameId(gameId)).thenReturn(teams);

        List<EventParticipantLocation> participants = teams.stream().map(team -> {
            Coordinates coordinates = new Coordinates();
            coordinates.setLongitude(4.9);
            coordinates.setLatitude(52.37);

            EventParticipantLocation pl = new EventParticipantLocation();
            pl.setTrackerId(Integer.valueOf(team.getTrackerId()));
            pl.setLocation(coordinates);
            pl.setLocationTime(fixTime.toString());
            return pl;
        }).toList();

        EventParticipantLocationsBody body = new EventParticipantLocationsBody();
        body.setEventParticipantLocations(participants);
        EventParticipantsLocationsResponse response = new EventParticipantsLocationsResponse();
        response.setEvent(body);

        when(locationApiClient.fetchParticipantLocations(eq(game.getLocationEndpointUrl()), any()))
            .thenReturn(response);
    }

    private LocationData fixFor(UUID teamId, Instant at) {
        LocationData fix = new LocationData();
        fix.setTeamId(teamId);
        fix.setGameId(gameId);
        fix.setLocationTime(at);
        return fix;
    }

    @Test
    void sweep_readsLastStoredFixesInOneQueryForTheWholeGame() throws LocationApiException {
        List<Team> teams = List.of(teamWithTracker("101"), teamWithTracker("102"), teamWithTracker("103"));
        feed(teams, Instant.now());
        when(locationDataRepository.findLatestByGameId(gameId)).thenReturn(List.of());

        scheduler.pollLocations();

        verify(locationDataRepository, times(1)).findLatestByGameId(gameId);
        verify(locationDataRepository, never()).findLatestByTeamId(any());
    }

    @Test
    void sweep_triggersAutoBackfill_whenTheSnapshotShowsAnOfflineGap() throws LocationApiException {
        Instant fixTime = Instant.now();
        Team team = teamWithTracker("101");
        feed(List.of(team), fixTime);
        when(locationDataRepository.findLatestByGameId(gameId))
            .thenReturn(List.of(fixFor(team.getId(), fixTime.minus(Duration.ofHours(1)))));

        scheduler.pollLocations();

        verify(locationBackfillService).triggerAutoBackfill(eq(gameId), eq(team.getId()), anyInt());
    }

    @Test
    void sweep_doesNotTriggerAutoBackfill_whenTheLastStoredFixIsRecent() throws LocationApiException {
        Instant fixTime = Instant.now();
        Team team = teamWithTracker("101");
        feed(List.of(team), fixTime);
        when(locationDataRepository.findLatestByGameId(gameId))
            .thenReturn(List.of(fixFor(team.getId(), fixTime.minus(Duration.ofMinutes(1)))));

        scheduler.pollLocations();

        verify(locationBackfillService, never()).triggerAutoBackfill(any(), any(), anyInt());
    }

    @Test
    void sweep_doesNotTriggerAutoBackfill_forATeamWithNoStoredFixYet() throws LocationApiException {
        Team team = teamWithTracker("101");
        feed(List.of(team), Instant.now());
        when(locationDataRepository.findLatestByGameId(gameId)).thenReturn(List.of());

        scheduler.pollLocations();

        verify(locationBackfillService, never()).triggerAutoBackfill(any(), any(), anyInt());
    }
}
