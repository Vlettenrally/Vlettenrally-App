package nl.vlettenrally.api.v1;

import jakarta.ws.rs.core.Response;
import nl.vlettenrally.data.dto.common.ErrorResponse;
import nl.vlettenrally.data.dto.game.TeamLocationResponse;
import nl.vlettenrally.service.LocationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class LocationResourceTest {

    @Mock
    LocationService locationService;

    @InjectMocks
    LocationResource locationResource;

    private UUID gameId;
    private UUID teamId;

    @BeforeEach
    void setUp() {
        gameId = UUID.randomUUID();
        teamId = UUID.randomUUID();
    }

    // ==================== /latest ====================

    @Test
    void getLatestTeamLocations_serviceReturnsList_returnsOkWithBody() {
        List<TeamLocationResponse> body = List.of(
            new TeamLocationResponse(teamId, "De Watervlooien", 52.37, 4.9, 10.0, 90.0, "2026-05-17T10:00:00Z"));
        when(locationService.getLatestTeamLocations(gameId)).thenReturn(Optional.of(body));

        try (Response response = locationResource.getLatestTeamLocations(gameId)) {
            assertEquals(Response.Status.OK.getStatusCode(), response.getStatus());
            assertEquals(body, response.getEntity());
        }
    }

    @Test
    void getLatestTeamLocations_serviceReturnsEmpty_returns404WithErrorResponse() {
        when(locationService.getLatestTeamLocations(gameId)).thenReturn(Optional.empty());

        try (Response response = locationResource.getLatestTeamLocations(gameId)) {
            assertEquals(Response.Status.NOT_FOUND.getStatusCode(), response.getStatus());
            ErrorResponse error = assertInstanceOf(ErrorResponse.class, response.getEntity());
            assertEquals("Game not found", error.message());
        }
    }

    // ==================== /team/{teamId}/history ====================

    @Test
    void getTeamLocationHistory_serviceReturnsList_returnsOkAndForwardsWindow() {
        List<TeamLocationResponse> body = List.of(
            new TeamLocationResponse(teamId, "De Watervlooien", 52.37, 4.9, null, null, "2026-05-17T10:00:00Z"),
            new TeamLocationResponse(teamId, "De Watervlooien", 52.38, 4.91, null, null, "2026-05-17T10:01:00Z"));
        when(locationService.getTeamLocationHistory(gameId, teamId, 30)).thenReturn(Optional.of(body));

        try (Response response = locationResource.getTeamLocationHistory(gameId, teamId, 30)) {
            assertEquals(Response.Status.OK.getStatusCode(), response.getStatus());
            assertEquals(body, response.getEntity());
            verify(locationService).getTeamLocationHistory(gameId, teamId, 30);
        }
    }

    @Test
    void getTeamLocationHistory_serviceReturnsEmpty_returns404() {
        when(locationService.getTeamLocationHistory(gameId, teamId, 60)).thenReturn(Optional.empty());

        try (Response response = locationResource.getTeamLocationHistory(gameId, teamId, 60)) {
            assertEquals(Response.Status.NOT_FOUND.getStatusCode(), response.getStatus());
            ErrorResponse error = assertInstanceOf(ErrorResponse.class, response.getEntity());
            assertEquals("Game or team not found", error.message());
        }
    }
}
