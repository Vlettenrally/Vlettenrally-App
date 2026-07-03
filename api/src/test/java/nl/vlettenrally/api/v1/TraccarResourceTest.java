package nl.vlettenrally.api.v1;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
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
        // Real mapper for the JSON-body parsing path (not a Mockito mock).
        resource.objectMapper = new com.fasterxml.jackson.databind.ObjectMapper();
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
        verify(locationIngestService).ingest(captor.capture(), eq("Team"), isNull());
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

        Team team = enabledTeam("secret-id");
        when(teamService.findByTraccarIdentifier("secret-id")).thenReturn(Optional.of(team));

        String body = "id=secret-id&lat=52.1&lon=5.2&timestamp=1700000000&altitude=12.0&speed=10.0&bearing=90.0&accuracy=8.0";
        Response response = resource.reportPost(body,
                null, null, null, null, null, null, null, null, null);

        assertEquals(200, response.getStatus());

        ArgumentCaptor<LocationData> captor = ArgumentCaptor.forClass(LocationData.class);
        verify(locationIngestService).ingest(captor.capture(), eq("Team"), isNull());
        LocationData reading = captor.getValue();
        assertEquals(52.1, reading.getLocation().getY(), 1e-9);
        assertEquals(5.2, reading.getLocation().getX(), 1e-9);
        // 10 knots -> 18.52 km/h
        assertEquals(18.52, reading.getSpeed().doubleValue(), 1e-6);
        assertEquals(90.0, reading.getHeading().doubleValue(), 1e-9);
    }

    @Test
    void reportPost_jsonBody_ingestsConvertedReading() {
        // A JSON object body is also accepted, regardless of content type.
        Team team = enabledTeam("secret-id");
        when(teamService.findByTraccarIdentifier("secret-id")).thenReturn(Optional.of(team));

        String body = "{\"id\":\"secret-id\",\"lat\":52.1,\"lon\":5.2,\"timestamp\":1700000000,"
            + "\"altitude\":12.0,\"speed\":10.0,\"bearing\":90.0,\"accuracy\":8.0}";
        Response response = resource.reportPost(body,
                null, null, null, null, null, null, null, null, null);

        assertEquals(200, response.getStatus());

        ArgumentCaptor<LocationData> captor = ArgumentCaptor.forClass(LocationData.class);
        verify(locationIngestService).ingest(captor.capture(), eq("Team"), isNull());
        LocationData reading = captor.getValue();
        assertEquals(52.1, reading.getLocation().getY(), 1e-9);
        assertEquals(5.2, reading.getLocation().getX(), 1e-9);
        assertEquals(18.52, reading.getSpeed().doubleValue(), 1e-6);
        assertEquals(90.0, reading.getHeading().doubleValue(), 1e-9);
    }

    @Test
    void reportPost_backgroundGeolocationJson_ingestsNestedReading() {
        // Real Transistorsoft background-geolocation payload: id is top-level
        // device_id, coords are nested, speed is m/s, and -1 means "unknown".
        Team team = enabledTeam("dev-123");
        when(teamService.findByTraccarIdentifier("dev-123")).thenReturn(Optional.of(team));

        String body = "{\"location\":{\"timestamp\":\"2026-06-12T13:14:07.164Z\",\"coords\":"
            + "{\"latitude\":52.0158884,\"longitude\":4.7094968,\"accuracy\":13.39,"
            + "\"speed\":-1,\"heading\":-1,\"altitude\":45.4}},\"device_id\":\"dev-123\"}";
        Response response = resource.reportPost(body,
                null, null, null, null, null, null, null, null, null);

        assertEquals(200, response.getStatus());

        ArgumentCaptor<LocationData> captor = ArgumentCaptor.forClass(LocationData.class);
        verify(locationIngestService).ingest(captor.capture(), eq("Team"), isNull());
        LocationData reading = captor.getValue();
        assertEquals(52.0158884, reading.getLocation().getY(), 1e-9);
        assertEquals(4.7094968, reading.getLocation().getX(), 1e-9);
        assertEquals(java.time.Instant.parse("2026-06-12T13:14:07.164Z"), reading.getLocationTime());
        // -1 sentinels become null rather than a bogus negative speed/heading.
        assertNull(reading.getSpeed());
        assertNull(reading.getHeading());
    }

    @Test
    void reportPost_backgroundGeolocationJson_convertsSpeedFromMetersPerSecond() {
        Team team = enabledTeam("dev-123");
        when(teamService.findByTraccarIdentifier("dev-123")).thenReturn(Optional.of(team));

        // 10 m/s -> 36 km/h.
        String body = "{\"location\":{\"timestamp\":\"2026-06-12T13:14:07.164Z\",\"coords\":"
            + "{\"latitude\":52.0,\"longitude\":4.7,\"speed\":10.0,\"heading\":270.0}},"
            + "\"device_id\":\"dev-123\"}";
        Response response = resource.reportPost(body,
                null, null, null, null, null, null, null, null, null);

        assertEquals(200, response.getStatus());

        ArgumentCaptor<LocationData> captor = ArgumentCaptor.forClass(LocationData.class);
        verify(locationIngestService).ingest(captor.capture(), eq("Team"), isNull());
        LocationData reading = captor.getValue();
        assertEquals(36.0, reading.getSpeed().doubleValue(), 1e-6);
        assertEquals(270.0, reading.getHeading().doubleValue(), 1e-9);
    }

    @Test
    void report_unknownIdentifier_doesNotIngest() {
        when(teamService.findByTraccarIdentifier("nope")).thenReturn(Optional.empty());

        Response response = resource.reportGet("nope", 52.1, 5.2, null,
            null, null, null, null, null);

        assertEquals(200, response.getStatus());
        verify(locationIngestService, never()).ingest(any(), any(), any());
    }

    @Test
    void report_disabledTeam_doesNotIngest() {
        Team team = enabledTeam("secret-id");
        team.setTraccarEnabled(false);
        when(teamService.findByTraccarIdentifier("secret-id")).thenReturn(Optional.of(team));

        Response response = resource.reportGet("secret-id", 52.1, 5.2, null,
            null, null, null, null, null);

        assertEquals(200, response.getStatus());
        verify(locationIngestService, never()).ingest(any(), any(), any());
    }

    @Test
    void report_missingRequiredParams_doesNotIngest() {
        Response response = resource.reportGet("secret-id", null, 5.2, null,
            null, null, null, null, null);

        assertEquals(200, response.getStatus());
        verify(locationIngestService, never()).ingest(any(), any(), any());
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
