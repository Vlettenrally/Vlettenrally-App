package nl.vlettenrally.api.v1;

import java.security.Principal;
import java.time.Duration;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import nl.vlettenrally.data.dto.auth.WebSocketTokenResponse;
import nl.vlettenrally.websockets.WebSocketTokenResult;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import static org.mockito.ArgumentMatchers.any;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import org.mockito.junit.jupiter.MockitoExtension;

import io.quarkus.security.identity.SecurityIdentity;
import io.smallrye.mutiny.Uni;
import jakarta.ws.rs.core.Response;
import nl.vlettenrally.data.dto.common.ErrorResponse;
import nl.vlettenrally.websockets.WebSocketTokenService;

/**
 * Unit tests for WebSocketTokenResource.
 * Tests REST endpoints for obtaining WebSocket tokens.
 */
@ExtendWith(MockitoExtension.class)
class WebSocketTokenResourceTest {

    @Mock
    WebSocketTokenService tokenService;

    @Mock
    SecurityIdentity securityIdentity;

    @Mock
    Principal principal;

    @InjectMocks
    WebSocketTokenResource resource;

    private final UUID adminId = UUID.randomUUID();
    private final UUID teamId = UUID.randomUUID();
    private final UUID gameId = UUID.randomUUID();

    // ========== Admin Token Tests ==========

    @Test
    void getAdminToken_success_returnsToken() {
        when(securityIdentity.getPrincipal()).thenReturn(principal);
        when(principal.getName()).thenReturn(adminId.toString());
        when(securityIdentity.getAttribute("name")).thenReturn("Test Admin");
        when(tokenService.generateAdminToken(adminId, "Test Admin"))
            .thenReturn(Uni.createFrom().item("admin-token-123"));

        WebSocketTokenResponse tokenResponse;
        try (Response response = resource.getAdminToken()
                .await().atMost(Duration.ofSeconds(2))) {

            assertEquals(200, response.getStatus());
            tokenResponse = (WebSocketTokenResponse) response.getEntity();
        }
        assertEquals("admin-token-123", tokenResponse.token());
        assertEquals(30, tokenResponse.expiresInSeconds());
    }

    @Test
    void getAdminToken_noName_usesDefaultName() {
        when(securityIdentity.getPrincipal()).thenReturn(principal);
        when(principal.getName()).thenReturn(adminId.toString());
        when(securityIdentity.getAttribute("name")).thenReturn(null);
        when(tokenService.generateAdminToken(adminId, "Admin"))
            .thenReturn(Uni.createFrom().item("admin-token-123"));

        try (Response response = resource.getAdminToken()
                .await().atMost(Duration.ofSeconds(2))) {

            assertEquals(200, response.getStatus());
        }
        verify(tokenService).generateAdminToken(adminId, "Admin");
    }

    @Test
    void getAdminToken_serviceError_returns500() {
        when(securityIdentity.getPrincipal()).thenReturn(principal);
        when(principal.getName()).thenReturn(adminId.toString());
        when(securityIdentity.getAttribute("name")).thenReturn("Admin");
        when(tokenService.generateAdminToken(any(), any()))
            .thenReturn(Uni.createFrom().failure(new RuntimeException("Service error")));

        ErrorResponse error;
        try (Response response = resource.getAdminToken()
                .await().atMost(Duration.ofSeconds(2))) {

            assertEquals(500, response.getStatus());
            error = (ErrorResponse) response.getEntity();
        }
        assertNotNull(error.message());
    }

    // ========== Team Token Tests ==========

    @Test
    void getTeamToken_asTeam_success_returnsToken() {
        when(securityIdentity.getPrincipal()).thenReturn(principal);
        when(principal.getName()).thenReturn(teamId.toString());
        when(securityIdentity.hasRole("admin")).thenReturn(false);
        when(tokenService.generateTeamToken(teamId, gameId))
            .thenReturn(Uni.createFrom().item(new WebSocketTokenResult.Success("team-token-123", 30)));

        WebSocketTokenResponse tokenResponse;
        try (Response response = resource.getTeamToken(gameId.toString(), teamId.toString())
                .await().atMost(Duration.ofSeconds(2))) {

            assertEquals(200, response.getStatus());
            tokenResponse = (WebSocketTokenResponse) response.getEntity();
        }
        assertEquals("team-token-123", tokenResponse.token());
        assertEquals(30, tokenResponse.expiresInSeconds());
    }

    @Test
    void getTeamToken_asAdmin_success_returnsToken() {
        when(securityIdentity.getPrincipal()).thenReturn(principal);
        when(principal.getName()).thenReturn(adminId.toString());
        when(securityIdentity.hasRole("admin")).thenReturn(true);
        when(tokenService.generateTeamToken(teamId, gameId))
            .thenReturn(Uni.createFrom().item(new WebSocketTokenResult.Success("team-token-456", 30)));

        WebSocketTokenResponse tokenResponse;
        try (Response response = resource.getTeamToken(gameId.toString(), teamId.toString())
                .await().atMost(Duration.ofSeconds(2))) {

            assertEquals(200, response.getStatus());
            tokenResponse = (WebSocketTokenResponse) response.getEntity();
        }
        assertEquals("team-token-456", tokenResponse.token());
    }

    @Test
    void getTeamToken_teamAccessingOtherTeam_returns403() {
        UUID otherTeamId = UUID.randomUUID();
        when(securityIdentity.getPrincipal()).thenReturn(principal);
        when(principal.getName()).thenReturn(teamId.toString());
        when(securityIdentity.hasRole("admin")).thenReturn(false);

        ErrorResponse error;
        try (Response response = resource.getTeamToken(gameId.toString(), otherTeamId.toString())
                .await().atMost(Duration.ofSeconds(2))) {

            assertEquals(403, response.getStatus());
            error = (ErrorResponse) response.getEntity();
        }
        assertEquals("Cannot get token for another team", error.message());
        verify(tokenService, never()).generateTeamToken(any(), any());
    }

    @Test
    void getTeamToken_invalidGameId_returns400() {
        // No stubbing needed - UUID parsing fails before any mock is used
        ErrorResponse error;
        try (Response response = resource.getTeamToken("not-a-uuid", teamId.toString())
                .await().atMost(Duration.ofSeconds(2))) {

            assertEquals(400, response.getStatus());
            error = (ErrorResponse) response.getEntity();
        }
        assertEquals("Invalid gameId or teamId format", error.message());
    }

    @Test
    void getTeamToken_invalidTeamId_returns400() {
        // No stubbing needed - UUID parsing fails before any mock is used
        try (Response response = resource.getTeamToken(gameId.toString(), "not-a-uuid")
                .await().atMost(Duration.ofSeconds(2))) {

            assertEquals(400, response.getStatus());
        }
    }

    @Test
    void getTeamToken_teamNotFound_returns404() {
        when(securityIdentity.getPrincipal()).thenReturn(principal);
        when(principal.getName()).thenReturn(teamId.toString());
        when(securityIdentity.hasRole("admin")).thenReturn(false);
        when(tokenService.generateTeamToken(teamId, gameId))
            .thenReturn(Uni.createFrom().item(new WebSocketTokenResult.TeamNotFound()));

        ErrorResponse error;
        try (Response response = resource.getTeamToken(gameId.toString(), teamId.toString())
                .await().atMost(Duration.ofSeconds(2))) {

            assertEquals(404, response.getStatus());
            error = (ErrorResponse) response.getEntity();
        }
        assertEquals("Team not found", error.message());
    }

    @Test
    void getTeamToken_teamGameMismatch_returns400() {
        when(securityIdentity.getPrincipal()).thenReturn(principal);
        when(principal.getName()).thenReturn(teamId.toString());
        when(securityIdentity.hasRole("admin")).thenReturn(false);
        when(tokenService.generateTeamToken(teamId, gameId))
            .thenReturn(Uni.createFrom().item(new WebSocketTokenResult.TeamGameMismatch()));

        ErrorResponse error;
        try (Response response = resource.getTeamToken(gameId.toString(), teamId.toString())
                .await().atMost(Duration.ofSeconds(2))) {

            assertEquals(400, response.getStatus());
            error = (ErrorResponse) response.getEntity();
        }
        assertEquals("Team does not belong to the specified game", error.message());
    }

    @Test
    void getTeamToken_serviceError_returns500() {
        when(securityIdentity.getPrincipal()).thenReturn(principal);
        when(principal.getName()).thenReturn(teamId.toString());
        when(securityIdentity.hasRole("admin")).thenReturn(false);
        when(tokenService.generateTeamToken(teamId, gameId))
            .thenReturn(Uni.createFrom().item(new WebSocketTokenResult.Error("Internal error")));

        ErrorResponse error;
        try (Response response = resource.getTeamToken(gameId.toString(), teamId.toString())
                .await().atMost(Duration.ofSeconds(2))) {

            assertEquals(500, response.getStatus());
            error = (ErrorResponse) response.getEntity();
        }
        assertEquals("Internal error", error.message());
    }

    @Test
    void getTeamToken_uniFailure_returns500() {
        when(securityIdentity.getPrincipal()).thenReturn(principal);
        when(principal.getName()).thenReturn(teamId.toString());
        when(securityIdentity.hasRole("admin")).thenReturn(false);
        when(tokenService.generateTeamToken(teamId, gameId))
            .thenReturn(Uni.createFrom().failure(new RuntimeException("Unexpected error")));

        try (Response response = resource.getTeamToken(gameId.toString(), teamId.toString())
                .await().atMost(Duration.ofSeconds(2))) {

            assertEquals(500, response.getStatus());
        }
    }
}



