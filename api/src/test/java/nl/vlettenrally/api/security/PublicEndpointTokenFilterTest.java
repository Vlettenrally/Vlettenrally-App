package nl.vlettenrally.api.security;

import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.core.Response;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link PublicEndpointTokenFilter}: fail-closed behaviour, token matching, and
 * acceptance of both the current and legacy header names.
 */
@ExtendWith(MockitoExtension.class)
class PublicEndpointTokenFilterTest {

    @Mock ContainerRequestContext requestContext;

    private PublicEndpointTokenFilter filter(String configuredToken) {
        PublicEndpointTokenFilter f = new PublicEndpointTokenFilter();
        // PublicEndpointConfig is a single-method interface, so a lambda stands in for it.
        f.config = () -> Optional.ofNullable(configuredToken);
        return f;
    }

    @Test
    void blankToken_failsClosed() {
        filter("  ").filter(requestContext);
        assertAborted(401);
    }

    @Test
    void missingHeader_returns401() {
        when(requestContext.getHeaderString(PublicEndpointTokenFilter.TOKEN_HEADER)).thenReturn(null);
        when(requestContext.getHeaderString(PublicEndpointTokenFilter.LEGACY_TOKEN_HEADER)).thenReturn(null);
        filter("secret").filter(requestContext);
        assertAborted(401);
    }

    @Test
    void blankHeader_returns401() {
        when(requestContext.getHeaderString(PublicEndpointTokenFilter.TOKEN_HEADER)).thenReturn("   ");
        when(requestContext.getHeaderString(PublicEndpointTokenFilter.LEGACY_TOKEN_HEADER)).thenReturn(null);
        filter("secret").filter(requestContext);
        assertAborted(401);
    }

    @Test
    void wrongToken_returns401() {
        when(requestContext.getHeaderString(PublicEndpointTokenFilter.TOKEN_HEADER)).thenReturn("nope");
        filter("secret").filter(requestContext);
        assertAborted(401);
    }

    @Test
    void correctToken_passesThrough() {
        when(requestContext.getHeaderString(PublicEndpointTokenFilter.TOKEN_HEADER)).thenReturn("secret");
        filter("secret").filter(requestContext);
        verify(requestContext, never()).abortWith(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void correctToken_onLegacyHeader_passesThrough() {
        when(requestContext.getHeaderString(PublicEndpointTokenFilter.TOKEN_HEADER)).thenReturn(null);
        when(requestContext.getHeaderString(PublicEndpointTokenFilter.LEGACY_TOKEN_HEADER)).thenReturn("secret");
        filter("secret").filter(requestContext);
        verify(requestContext, never()).abortWith(org.mockito.ArgumentMatchers.any());
    }

    private void assertAborted(int expectedStatus) {
        ArgumentCaptor<Response> captor = ArgumentCaptor.forClass(Response.class);
        verify(requestContext).abortWith(captor.capture());
        assertEquals(expectedStatus, captor.getValue().getStatus());
    }
}
