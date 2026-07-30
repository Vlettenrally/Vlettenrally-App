package nl.vlettenrally.api.security;

import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.core.MultivaluedHashMap;
import jakarta.ws.rs.core.MultivaluedMap;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.UriInfo;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link PublicMapTokenFilter}: fail-closed behaviour and the game binding that makes
 * a narrowcasting link useless on any game other than the one it was minted for.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class PublicMapTokenFilterTest {

    private static final UUID GAME = UUID.fromString("a1b2c3d4-e5f6-7890-abcd-ef1234567890");
    private static final UUID OTHER_GAME = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final String SECRET = "secret";

    @Mock ContainerRequestContext requestContext;
    @Mock UriInfo uriInfo;

    private PublicMapTokenService tokenService(String configuredSecret) {
        PublicMapTokenService s = new PublicMapTokenService();
        s.config = () -> Optional.ofNullable(configuredSecret);
        return s;
    }

    /** Wire up a request for {@code gameId} carrying {@code header}, and run the filter. */
    private void runFilter(String configuredSecret, String rawGameId, String header) {
        MultivaluedMap<String, String> pathParams = new MultivaluedHashMap<>();
        if (rawGameId != null) {
            pathParams.putSingle(PublicMapTokenFilter.GAME_ID_PARAM, rawGameId);
        }
        when(uriInfo.getPathParameters()).thenReturn(pathParams);
        when(requestContext.getUriInfo()).thenReturn(uriInfo);
        when(requestContext.getHeaderString(PublicMapTokenFilter.TOKEN_HEADER)).thenReturn(header);

        PublicMapTokenFilter filter = new PublicMapTokenFilter();
        filter.tokenService = tokenService(configuredSecret);
        filter.filter(requestContext);
    }

    private String validTokenFor(UUID gameId) {
        return tokenService(SECRET).tokenFor(gameId).orElseThrow();
    }

    @Test
    void noSecretConfigured_failsClosed() {
        runFilter(null, GAME.toString(), "anything");
        assertAborted(401);
    }

    @Test
    void blankSecretConfigured_failsClosed() {
        runFilter("  ", GAME.toString(), "anything");
        assertAborted(401);
    }

    @Test
    void missingHeader_returns401() {
        runFilter(SECRET, GAME.toString(), null);
        assertAborted(401);
    }

    @Test
    void blankHeader_returns401() {
        runFilter(SECRET, GAME.toString(), "   ");
        assertAborted(401);
    }

    @Test
    void wrongToken_returns401() {
        runFilter(SECRET, GAME.toString(), "nope");
        assertAborted(401);
    }

    @Test
    void tokenForAnotherGame_returns401() {
        runFilter(SECRET, GAME.toString(), validTokenFor(OTHER_GAME));
        assertAborted(401);
    }

    @Test
    void malformedGameId_returns401() {
        runFilter(SECRET, "not-a-uuid", validTokenFor(GAME));
        assertAborted(401);
    }

    @Test
    void missingGameIdParam_returns401() {
        runFilter(SECRET, null, validTokenFor(GAME));
        assertAborted(401);
    }

    @Test
    void correctToken_passesThrough() {
        runFilter(SECRET, GAME.toString(), validTokenFor(GAME));
        verify(requestContext, never()).abortWith(any());
    }

    private void assertAborted(int expectedStatus) {
        ArgumentCaptor<Response> captor = ArgumentCaptor.forClass(Response.class);
        verify(requestContext).abortWith(captor.capture());
        assertEquals(expectedStatus, captor.getValue().getStatus());
    }
}
