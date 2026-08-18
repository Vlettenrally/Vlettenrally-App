package nl.vlettenrally.api.v1;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.mock;

import java.security.Principal;
import java.util.UUID;

import org.eclipse.microprofile.jwt.JsonWebToken;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.SecurityContext;
import nl.vlettenrally.data.dto.auth.RefreshOutcome;
import nl.vlettenrally.data.dto.auth.RefreshTokenRequest;
import nl.vlettenrally.data.dto.auth.TokenResult;
import nl.vlettenrally.service.AuthService;

/**
 * How /auth/refresh maps refresh outcomes onto status codes.
 * <p>
 * The split matters more than it looks: a 401 ends the session on every client, so collapsing
 * "try again in a moment" into 401 logged teams out over a duplicate in-flight refresh.
 */
@ExtendWith(MockitoExtension.class)
class AuthResourceTest {

    @Mock
    AuthService authService;

    @Mock
    JsonWebToken jwt;

    @InjectMocks
    AuthResource resource;

    @Test
    void refresh_returnsTokensOnSuccess() {
        when(authService.refreshToken("live"))
            .thenReturn(new RefreshOutcome.Success(
                new TokenResult("access", "rotated", 3600L, "subject", "name", "team")));

        Response response = resource.refreshToken(new RefreshTokenRequest("live"));

        assertEquals(200, response.getStatus());
    }

    @Test
    void refresh_returnsRetryableAs503WithRetryAfter() {
        when(authService.refreshToken("duplicate"))
            .thenReturn(new RefreshOutcome.Retryable("duplicate refresh in flight"));

        Response response = resource.refreshToken(new RefreshTokenRequest("duplicate"));

        assertEquals(503, response.getStatus(), "a retryable refresh must not end the session");
        assertEquals("2", response.getHeaderString("Retry-After"));
    }

    @Test
    void refresh_returnsRejectedAs401() {
        when(authService.refreshToken("dead"))
            .thenReturn(new RefreshOutcome.Rejected("unknown refresh token"));

        Response response = resource.refreshToken(new RefreshTokenRequest("dead"));

        assertEquals(401, response.getStatus());
    }

    @Test
    void refresh_requiresATokenInTheBody() {
        Response response = resource.refreshToken(new RefreshTokenRequest(null));

        assertEquals(400, response.getStatus());
    }

    @Test
    void logout_revokesOnlyTheDeviceThatIsLoggingOut() {
        UUID teamId = UUID.randomUUID();
        UUID deviceId = UUID.randomUUID();
        when(jwt.getClaim("did")).thenReturn(deviceId.toString());

        Response response = resource.logout(securityContextFor(teamId));

        assertEquals(200, response.getStatus());
        verify(authService).revokeDeviceTokens(teamId, deviceId);
        // A team's devices share one subject, and a stranded crew's only way back in is the
        // rally desk. One person leaving the app must not log out the boat.
        verify(authService, never()).revokeTokens(any());
    }

    @Test
    void logout_withoutADeviceClaimRevokesTheSubject() {
        // Admins carry no device binding, and logging back in only costs them a password.
        UUID adminId = UUID.randomUUID();
        when(jwt.getClaim("did")).thenReturn(null);

        Response response = resource.logout(securityContextFor(adminId));

        assertEquals(200, response.getStatus());
        verify(authService).revokeTokens(adminId);
        verify(authService, never()).revokeDeviceTokens(any(), any());
    }

    @Test
    void logout_withAMalformedDeviceClaimStillRevokesSomething() {
        UUID subject = UUID.randomUUID();
        when(jwt.getClaim("did")).thenReturn("not-a-uuid");

        Response response = resource.logout(securityContextFor(subject));

        assertEquals(200, response.getStatus());
        verify(authService).revokeTokens(subject);
    }

    private SecurityContext securityContextFor(UUID subject) {
        Principal principal = subject::toString;
        SecurityContext securityContext = mock(SecurityContext.class);
        when(securityContext.getUserPrincipal()).thenReturn(principal);
        return securityContext;
    }
}
