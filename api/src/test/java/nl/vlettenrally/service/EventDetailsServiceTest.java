package nl.vlettenrally.service;

import nl.vlettenrally.client.LocationApiClient;
import nl.vlettenrally.client.dto.Coordinates;
import nl.vlettenrally.client.dto.legendstracking.EventCheckpoint;
import nl.vlettenrally.client.dto.legendstracking.EventDetailsBody;
import nl.vlettenrally.client.dto.legendstracking.EventDetailsResponse;
import nl.vlettenrally.client.dto.legendstracking.EventParticipant;
import nl.vlettenrally.data.domain.Checkpoint;
import nl.vlettenrally.data.domain.CircleCheckpoint;
import nl.vlettenrally.data.domain.PolygonCheckpoint;
import nl.vlettenrally.data.domain.Team;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class EventDetailsServiceTest {

    @Mock
    LocationApiClient locationApiClient;

    @Mock
    CheckpointService checkpointService;

    @Mock
    TeamService teamService;

    @InjectMocks
    EventDetailsService eventDetailsService;

    private UUID gameId;

    @BeforeEach
    void setUp() {
        gameId = UUID.randomUUID();
    }

    @Test
    void importCheckpoints_matchesExistingZoneByLocationAndLinksExternalId() throws Exception {
        CircleCheckpoint existing = checkpoint("Old name", null, 52.10000, 5.10000);
        when(checkpointService.findByGameId(gameId)).thenReturn(new ArrayList<>(List.of(existing)));
        when(checkpointService.save(any(Checkpoint.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(locationApiClient.fetchDetails("https://tracker/details")).thenReturn(responseWithCheckpoints(
            eventCheckpoint(123, "New name", 52.10005, 5.10005)
        ));

        ImportResult result = eventDetailsService.importCheckpoints(gameId, "https://tracker/details");

        assertEquals(0, result.created());
        assertEquals(1, result.updated());
        assertEquals(0, result.errors());

        ArgumentCaptor<Checkpoint> captor = ArgumentCaptor.forClass(Checkpoint.class);
        verify(checkpointService).save(captor.capture());
        Checkpoint saved = captor.getValue();
        assertEquals(existing.getId(), saved.getId());
        assertEquals("123", saved.getExternalId());
        assertEquals("New name", saved.getName());
    }

    @Test
    void importCheckpoints_choosesNearestZoneWhenMultipleAreWithin50Meters() throws Exception {
        CircleCheckpoint nearest = checkpoint("Nearest", null, 52.10000, 5.10000);
        CircleCheckpoint farther = checkpoint("Farther", null, 52.10035, 5.10035);
        when(checkpointService.findByGameId(gameId)).thenReturn(new ArrayList<>(List.of(nearest, farther)));
        when(checkpointService.save(any(Checkpoint.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(locationApiClient.fetchDetails("https://tracker/details")).thenReturn(responseWithCheckpoints(
            eventCheckpoint(456, "Imported", 52.10003, 5.10003)
        ));

        ImportResult result = eventDetailsService.importCheckpoints(gameId, "https://tracker/details");

        assertEquals(0, result.created());
        assertEquals(1, result.updated());
        assertEquals(0, result.errors());

        ArgumentCaptor<Checkpoint> captor = ArgumentCaptor.forClass(Checkpoint.class);
        verify(checkpointService, times(1)).save(captor.capture());
        assertEquals(nearest.getId(), captor.getValue().getId());
        assertEquals("456", captor.getValue().getExternalId());
    }

    @Test
    void importCheckpoints_createsZoneWhenNoLocationMatchExists() throws Exception {
        CircleCheckpoint existing = checkpoint("Existing", null, 52.10000, 5.10000);
        when(checkpointService.findByGameId(gameId)).thenReturn(new ArrayList<>(List.of(existing)));
        when(checkpointService.save(any(Checkpoint.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(locationApiClient.fetchDetails("https://tracker/details")).thenReturn(responseWithCheckpoints(
            eventCheckpoint(789, "Brand new", 52.20000, 5.20000)
        ));

        ImportResult result = eventDetailsService.importCheckpoints(gameId, "https://tracker/details");

        assertEquals(1, result.created());
        assertEquals(0, result.updated());
        assertEquals(0, result.errors());
        verify(checkpointService, times(1)).save(any(Checkpoint.class));
    }

    @Test
    void importCheckpoints_newCircleZoneUsesApiRadius() throws Exception {
        when(checkpointService.findByGameId(gameId)).thenReturn(new ArrayList<>());
        when(checkpointService.save(any(Checkpoint.class))).thenAnswer(invocation -> invocation.getArgument(0));
        EventCheckpoint detail = eventCheckpoint(789, "Radius zone", 52.20000, 5.20000);
        detail.setRadius(120.0);
        when(locationApiClient.fetchDetails("https://tracker/details"))
            .thenReturn(responseWithCheckpoints(detail));

        ImportResult result = eventDetailsService.importCheckpoints(gameId, "https://tracker/details");

        assertEquals(1, result.created());
        ArgumentCaptor<Checkpoint> captor = ArgumentCaptor.forClass(Checkpoint.class);
        verify(checkpointService).save(captor.capture());
        Checkpoint saved = captor.getValue();
        assertInstanceOf(CircleCheckpoint.class, saved);
        assertEquals(120.0, ((CircleCheckpoint) saved).getRadiusMeters(), 1e-9);
    }

    @Test
    void importCheckpoints_newPolygonZoneFromPolygonCoordinates() throws Exception {
        when(checkpointService.findByGameId(gameId)).thenReturn(new ArrayList<>());
        when(checkpointService.save(any(Checkpoint.class))).thenAnswer(invocation -> invocation.getArgument(0));
        EventCheckpoint detail = eventCheckpoint(789, "Polygon zone", 52.025, 4.675);
        // Flat lng,lat pairs — a triangle.
        detail.setPolygonCoordinates("4.668807,52.025402,4.681167,52.030261,4.681682,52.020860");
        when(locationApiClient.fetchDetails("https://tracker/details"))
            .thenReturn(responseWithCheckpoints(detail));

        ImportResult result = eventDetailsService.importCheckpoints(gameId, "https://tracker/details");

        assertEquals(1, result.created());
        ArgumentCaptor<Checkpoint> captor = ArgumentCaptor.forClass(Checkpoint.class);
        verify(checkpointService).save(captor.capture());
        Checkpoint saved = captor.getValue();
        assertInstanceOf(PolygonCheckpoint.class, saved);
        var polygon = ((PolygonCheckpoint) saved).getZonePolygon();
        // 3 vertices + the closing point repeated, parsed as (x=lng, y=lat).
        assertEquals(4, polygon.getExteriorRing().getNumPoints());
        assertEquals(4.668807, polygon.getExteriorRing().getCoordinateN(0).x, 1e-9);
        assertEquals(52.025402, polygon.getExteriorRing().getCoordinateN(0).y, 1e-9);
    }

    @Test
    void importCheckpoints_matchedZoneKeepsAppGeometry() throws Exception {
        // App-authored circle with a deliberate, non-default radius.
        CircleCheckpoint existing = checkpoint("Hand drawn", null, 52.10000, 5.10000);
        existing.setRadiusMeters(25.0);
        when(checkpointService.findByGameId(gameId)).thenReturn(new ArrayList<>(List.of(existing)));
        when(checkpointService.save(any(Checkpoint.class))).thenAnswer(invocation -> invocation.getArgument(0));
        // API checkpoint matches by location but carries its own radius + polygon.
        EventCheckpoint detail = eventCheckpoint(123, "New name", 52.10005, 5.10005);
        detail.setRadius(200.0);
        detail.setPolygonCoordinates("4.668807,52.025402,4.681167,52.030261,4.681682,52.020860");
        when(locationApiClient.fetchDetails("https://tracker/details"))
            .thenReturn(responseWithCheckpoints(detail));

        eventDetailsService.importCheckpoints(gameId, "https://tracker/details");

        ArgumentCaptor<Checkpoint> captor = ArgumentCaptor.forClass(Checkpoint.class);
        verify(checkpointService).save(captor.capture());
        Checkpoint saved = captor.getValue();
        assertEquals(existing.getId(), saved.getId());
        // Still the app circle and its radius — API geometry did not overwrite it.
        assertInstanceOf(CircleCheckpoint.class, saved);
        assertEquals(25.0, ((CircleCheckpoint) saved).getRadiusMeters(), 1e-9);
        assertEquals("123", saved.getExternalId());
    }

    @Test
    void linkParticipants_returnsCountsAndAffectedItems() throws Exception {
        Team alpha = team("101");
        Team bravo = team("Bravo");
        when(teamService.findByGameId(gameId)).thenReturn(List.of(alpha, bravo));
        when(locationApiClient.fetchDetails("https://tracker/details")).thenReturn(responseWithParticipants(
            eventParticipant(1001, 101, "Ignored"),
            eventParticipant(1002, null, "  bravo "),
            eventParticipant(1003, null, "No team match")
        ));

        ParticipantLinkResult result = eventDetailsService.linkParticipants(gameId, "https://tracker/details");

        assertEquals(2, result.linked());
        assertEquals(1, result.unmatched());
        assertEquals(List.of("101", "Bravo"), result.linkedTeams());
        assertEquals(List.of("No team match"), result.unmatchedParticipants());
        verify(teamService, times(2)).save(any(Team.class));
    }

    @Test
    void linkParticipants_unmatchedLabelsUseConfiguredFallbackOrder() throws Exception {
        when(teamService.findByGameId(gameId)).thenReturn(List.<Team>of());
        when(locationApiClient.fetchDetails("https://tracker/details")).thenReturn(responseWithParticipants(
            eventParticipant(2001, 88, "  Bib Label  "),
            eventParticipant(2002, 77, null),
            eventParticipant(2003, null, "   ")
        ));

        ParticipantLinkResult result = eventDetailsService.linkParticipants(gameId, "https://tracker/details");

        assertEquals(0, result.linked());
        assertEquals(3, result.unmatched());
        assertEquals(List.of("Bib Label", "77", "2003"), result.unmatchedParticipants());
    }

    private static EventDetailsResponse responseWithCheckpoints(EventCheckpoint... checkpoints) {
        EventDetailsBody body = new EventDetailsBody();
        body.setEventCheckpoints(List.of(checkpoints));
        EventDetailsResponse response = new EventDetailsResponse();
        response.setEvent(body);
        return response;
    }

    private static EventDetailsResponse responseWithParticipants(EventParticipant... participants) {
        EventDetailsBody body = new EventDetailsBody();
        body.setEventParticipants(List.of(participants));
        EventDetailsResponse response = new EventDetailsResponse();
        response.setEvent(body);
        return response;
    }

    private CircleCheckpoint checkpoint(String name, String externalId, double lat, double lng) {
        CircleCheckpoint cp = new CircleCheckpoint();
        cp.setId(UUID.randomUUID());
        cp.setGameId(gameId);
        cp.setName(name);
        cp.setExternalId(externalId);
        cp.setLatitude(lat);
        cp.setLongitude(lng);
        return cp;
    }

    private Team team(String name) {
        Team team = new Team();
        team.setId(UUID.randomUUID());
        team.setGameId(gameId);
        team.setName(name);
        return team;
    }

    private static EventCheckpoint eventCheckpoint(int id, String name, double lat, double lng) {
        EventCheckpoint checkpoint = new EventCheckpoint();
        checkpoint.setId(id);
        checkpoint.setName(name);
        Coordinates coordinates = new Coordinates();
        coordinates.setLatitude(lat);
        coordinates.setLongitude(lng);
        checkpoint.setLocation(coordinates);
        return checkpoint;
    }

    private static EventParticipant eventParticipant(int participantId, Integer bib, String bibLabel) {
        EventParticipant participant = new EventParticipant();
        participant.setEventParticipantsId(participantId);
        participant.setBib(bib);
        participant.setBibLabel(bibLabel);
        return participant;
    }
}
