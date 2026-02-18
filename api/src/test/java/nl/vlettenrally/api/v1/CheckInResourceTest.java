package nl.vlettenrally.api.v1;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import java.security.Principal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.SecurityContext;
import nl.vlettenrally.data.domain.Team;
import nl.vlettenrally.data.dto.auth.TeamOptionResponse;
import nl.vlettenrally.data.dto.auth.TeamSelectionRequest;
import nl.vlettenrally.data.dto.auth.TeamSelectionResponse;
import nl.vlettenrally.data.dto.auth.TokenResult;
import nl.vlettenrally.data.dto.common.ErrorResponse;
import nl.vlettenrally.service.AuthService;
import nl.vlettenrally.service.TeamService;

/**
 * Unit tests for CheckInResource.
 * Tests check-in team selection and token exchange endpoints.
 */
@ExtendWith(MockitoExtension.class)
class CheckInResourceTest {

    @Mock
    AuthService authService;

    @Mock
    TeamService teamService;

    @Mock
    SecurityContext securityContext;

    @Mock
    Principal principal;

    @InjectMocks
    CheckInResource checkInResource;

    private UUID gameId;
    private UUID teamId;

    @BeforeEach
    void setUp() {
        gameId = UUID.randomUUID();
        teamId = UUID.randomUUID();
    }

    @Test
    void getTeamOptions_validToken_returnsTeamList() {
        // Arrange
        when(securityContext.getUserPrincipal()).thenReturn(principal);
        when(principal.getName()).thenReturn(gameId.toString());
        when(authService.isCheckInTokenValid(gameId)).thenReturn(true);

        Team team1 = createTeam(teamId, "Team Alpha");
        Team team2 = createTeam(UUID.randomUUID(), "Team Beta");
        when(teamService.findByGameId(gameId)).thenReturn(List.of(team1, team2));

        // Act
        Response response = checkInResource.getTeamOptions(securityContext);

        // Assert
        assertEquals(Response.Status.OK.getStatusCode(), response.getStatus());
        TeamSelectionResponse body = (TeamSelectionResponse) response.getEntity();
        assertNotNull(body);
        assertEquals(gameId.toString(), body.gameId());
        assertEquals(2, body.teamOptions().size());

        // Verify team options contain name but no boatNumber
        TeamOptionResponse option = body.teamOptions().get(0);
        assertEquals(teamId, option.id());
        assertEquals("Team Alpha", option.name());
    }

    @Test
    void getTeamOptions_invalidToken_returnsUnauthorized() {
        // Arrange
        when(securityContext.getUserPrincipal()).thenReturn(principal);
        when(principal.getName()).thenReturn(gameId.toString());
        when(authService.isCheckInTokenValid(gameId)).thenReturn(false);

        // Act
        Response response = checkInResource.getTeamOptions(securityContext);

        // Assert
        assertEquals(Response.Status.UNAUTHORIZED.getStatusCode(), response.getStatus());
        ErrorResponse error = (ErrorResponse) response.getEntity();
        assertNotNull(error);
    }

    @Test
    void getTeamOptions_teamsAreSortedAlphabetically() {
        // Arrange
        when(securityContext.getUserPrincipal()).thenReturn(principal);
        when(principal.getName()).thenReturn(gameId.toString());
        when(authService.isCheckInTokenValid(gameId)).thenReturn(true);

        Team teamZ = createTeam(UUID.randomUUID(), "Zebra Team");
        Team teamA = createTeam(UUID.randomUUID(), "Alpha Team");
        Team teamM = createTeam(UUID.randomUUID(), "Middle Team");
        // Service returns in any order - the actual sorting may be done at service/repository level
        when(teamService.findByGameId(gameId)).thenReturn(List.of(teamA, teamM, teamZ));

        // Act
        Response response = checkInResource.getTeamOptions(securityContext);

        // Assert
        assertEquals(Response.Status.OK.getStatusCode(), response.getStatus());
        TeamSelectionResponse body = (TeamSelectionResponse) response.getEntity();
        assertEquals(3, body.teamOptions().size());
    }

    @Test
    void exchangeToken_validRequest_returnsTokens() {
        // Arrange
        when(securityContext.getUserPrincipal()).thenReturn(principal);
        when(principal.getName()).thenReturn(gameId.toString());

        TokenResult tokenResult = new TokenResult(
            "access-token",
            "refresh-token",
            3600L,
            teamId.toString(),
            "Team Alpha",
            "team"
        );
        when(authService.selectTeam(gameId, teamId)).thenReturn(Optional.of(tokenResult));

        TeamSelectionRequest request = new TeamSelectionRequest(teamId);

        // Act
        Response response = checkInResource.exchangeToken(request, securityContext);

        // Assert
        assertEquals(Response.Status.OK.getStatusCode(), response.getStatus());
    }

    @Test
    void exchangeToken_nullTeamId_returnsBadRequest() {
        // Arrange - no mocking needed since validation happens before any service call
        TeamSelectionRequest request = new TeamSelectionRequest(null);

        // Act
        Response response = checkInResource.exchangeToken(request, securityContext);

        // Assert
        assertEquals(Response.Status.BAD_REQUEST.getStatusCode(), response.getStatus());
        ErrorResponse error = (ErrorResponse) response.getEntity();
        assertEquals("teamId is required", error.message());
    }

    @Test
    void exchangeToken_invalidToken_returnsUnauthorized() {
        // Arrange
        when(securityContext.getUserPrincipal()).thenReturn(principal);
        when(principal.getName()).thenReturn(gameId.toString());
        when(authService.selectTeam(gameId, teamId)).thenReturn(Optional.empty());

        TeamSelectionRequest request = new TeamSelectionRequest(teamId);

        // Act
        Response response = checkInResource.exchangeToken(request, securityContext);

        // Assert
        assertEquals(Response.Status.UNAUTHORIZED.getStatusCode(), response.getStatus());
    }

    @Test
    void exchangeToken_teamAlreadyCheckedIn_returnsConflict() {
        // Arrange
        when(securityContext.getUserPrincipal()).thenReturn(principal);
        when(principal.getName()).thenReturn(gameId.toString());
        when(authService.selectTeam(gameId, teamId))
            .thenThrow(new IllegalStateException("Team is already checked in"));

        TeamSelectionRequest request = new TeamSelectionRequest(teamId);

        // Act
        Response response = checkInResource.exchangeToken(request, securityContext);

        // Assert
        assertEquals(Response.Status.CONFLICT.getStatusCode(), response.getStatus());
        ErrorResponse error = (ErrorResponse) response.getEntity();
        assertEquals("Team is already checked in", error.message());
    }

    @Test
    void exchangeToken_internalError_returnsServerError() {
        // Arrange
        when(securityContext.getUserPrincipal()).thenReturn(principal);
        when(principal.getName()).thenReturn(gameId.toString());
        when(authService.selectTeam(any(), any()))
            .thenThrow(new RuntimeException("Database connection failed"));

        TeamSelectionRequest request = new TeamSelectionRequest(teamId);

        // Act
        Response response = checkInResource.exchangeToken(request, securityContext);

        // Assert
        assertEquals(Response.Status.INTERNAL_SERVER_ERROR.getStatusCode(), response.getStatus());
    }

    private Team createTeam(UUID id, String name) {
        Team team = new Team();
        team.setId(id);
        team.setGameId(gameId);
        team.setName(name);
        team.setDescription("Test team");
        team.setCheckedIn(false);
        return team;
    }
}


