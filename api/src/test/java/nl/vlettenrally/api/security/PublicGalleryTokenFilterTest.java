package nl.vlettenrally.api.security;

import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.core.HttpHeaders;
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
 * Unit tests for {@link PublicGalleryTokenFilter}: fail-closed behaviour and token matching.
 */
@ExtendWith(MockitoExtension.class)
class PublicGalleryTokenFilterTest {

    @Mock ContainerRequestContext requestContext;

    private PublicGalleryTokenFilter filter(String configuredToken) {
        PublicGalleryTokenFilter f = new PublicGalleryTokenFilter();
        // PublicGalleryConfig is a single-method interface, so a lambda stands in for it.
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
        when(requestContext.getHeaderString(HttpHeaders.AUTHORIZATION)).thenReturn(null);
        filter("secret").filter(requestContext);
        assertAborted(401);
    }

    @Test
    void nonBearerHeader_returns401() {
        when(requestContext.getHeaderString(HttpHeaders.AUTHORIZATION)).thenReturn("secret");
        filter("secret").filter(requestContext);
        assertAborted(401);
    }

    @Test
    void wrongToken_returns401() {
        when(requestContext.getHeaderString(HttpHeaders.AUTHORIZATION)).thenReturn("Bearer nope");
        filter("secret").filter(requestContext);
        assertAborted(401);
    }

    @Test
    void correctToken_passesThrough() {
        when(requestContext.getHeaderString(HttpHeaders.AUTHORIZATION)).thenReturn("Bearer secret");
        filter("secret").filter(requestContext);
        verify(requestContext, never()).abortWith(org.mockito.ArgumentMatchers.any());
    }

    private void assertAborted(int expectedStatus) {
        ArgumentCaptor<Response> captor = ArgumentCaptor.forClass(Response.class);
        verify(requestContext).abortWith(captor.capture());
        assertEquals(expectedStatus, captor.getValue().getStatus());
    }
}
