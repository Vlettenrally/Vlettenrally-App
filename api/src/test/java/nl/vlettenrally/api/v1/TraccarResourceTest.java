package nl.vlettenrally.api.v1;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import jakarta.ws.rs.core.Response;
import nl.vlettenrally.data.domain.LocationData;
import nl.vlettenrally.data.domain.Team;
import nl.vlettenrally.service.LocationIngestService;
import nl.vlettenrally.service.TeamService;

/**
 * Unit tests for the Traccar/OsmAnd ingestion endpoint.
 */
@ExtendWith(MockitoExtension.class)
class TraccarResourceTest {

    @Mock
    TeamService teamService;

    @Mock
    LocationIngestService locationIngestService;

    @InjectMocks
    TraccarResource resource;

    private UUID teamId;
    private UUID gameId;

    @BeforeEach
    void setUp() {
        teamId = UUID.randomUUID();
        gameId = UUID.randomUUID();
    }

    @Test
    void report_enabledTeam_ingestsConvertedReading() {
        Team team = enabledTeam("secret-id");
        when(teamService.findByTraccarIdentifier("secret-id")).thenReturn(Optional.of(team));

        // speed 10 knots, bearing 90, at epoch second 1_700_000_000
        Response response = resource.reportGet("secret-id", 52.1, 5.2, "1700000000",
            12.0, 10.0, 90.0, 8.0, null);

        assertEquals(200, response.getStatus());

        ArgumentCaptor<LocationData> captor = ArgumentCaptor.forClass(LocationData.class);
        verify(locationIngestService).ingest(captor.capture(), eq("Team"));
        LocationData reading = captor.getValue();
        assertEquals(teamId, reading.getTeamId());
        assertEquals(gameId, reading.getGameId());
        assertEquals(52.1, reading.getLocation().getY(), 1e-9);
        assertEquals(5.2, reading.getLocation().getX(), 1e-9);
        // 10 knots -> 18.52 km/h
        assertEquals(18.52, reading.getSpeed().doubleValue(), 1e-6);
        assertEquals(90.0, reading.getHeading().doubleValue(), 1e-9);
    }

    @Test
    void reportPost_formBody_ingestsConvertedReading() {
        // The official Traccar Client POSTs OsmAnd values as a form body; query
        // params are all null. The form values must still be picked up.
        Team team = enabledTeam("secret-id");
        when(teamService.findByTraccarIdentifier("secret-id")).thenReturn(Optional.of(team));

        Response response = resource.reportPost(
            null, "secret-id",
            null, 52.1,
            null, 5.2,
            null, "1700000000",
            null, 12.0,
            null, 10.0,
            null, 90.0,
            null, 8.0,
            null, null);

        assertEquals(200, response.getStatus());

        ArgumentCaptor<LocationData> captor = ArgumentCaptor.forClass(LocationData.class);
        verify(locationIngestService).ingest(captor.capture(), eq("Team"));
        LocationData reading = captor.getValue();
        assertEquals(52.1, reading.getLocation().getY(), 1e-9);
        assertEquals(5.2, reading.getLocation().getX(), 1e-9);
        assertEquals(18.52, reading.getSpeed().doubleValue(), 1e-6);
    }

    @Test
    void report_unknownIdentifier_doesNotIngest() {
        when(teamService.findByTraccarIdentifier("nope")).thenReturn(Optional.empty());

        Response response = resource.reportGet("nope", 52.1, 5.2, null,
            null, null, null, null, null);

        assertEquals(200, response.getStatus());
        verify(locationIngestService, never()).ingest(any(), any());
    }

    @Test
    void report_disabledTeam_doesNotIngest() {
        Team team = enabledTeam("secret-id");
        team.setTraccarEnabled(false);
        when(teamService.findByTraccarIdentifier("secret-id")).thenReturn(Optional.of(team));

        Response response = resource.reportGet("secret-id", 52.1, 5.2, null,
            null, null, null, null, null);

        assertEquals(200, response.getStatus());
        verify(locationIngestService, never()).ingest(any(), any());
    }

    @Test
    void report_missingRequiredParams_doesNotIngest() {
        Response response = resource.reportGet("secret-id", null, 5.2, null,
            null, null, null, null, null);

        assertEquals(200, response.getStatus());
        verify(locationIngestService, never()).ingest(any(), any());
    }

    private Team enabledTeam(String identifier) {
        Team team = new Team();
        team.setId(teamId);
        team.setGameId(gameId);
        team.setName("Team");
        team.setTraccarEnabled(true);
        team.setTraccarIdentifier(identifier);
        return team;
    }
}
