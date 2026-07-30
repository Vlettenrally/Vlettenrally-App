package nl.vlettenrally.api.v1;

import jakarta.ws.rs.core.Response;
import nl.vlettenrally.data.dto.game.PublicMapResponse;
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
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link PublicMapResource}. The token gate lives in
 * {@link nl.vlettenrally.api.security.PublicMapTokenFilter} and the assembly in
 * {@link PublicMapCache}, so this covers the resource's own contract: serve the payload, 404 an
 * unknown game.
 */
@ExtendWith(MockitoExtension.class)
class PublicMapResourceTest {

    private static final UUID GAME_ID = UUID.fromString("a1b2c3d4-e5f6-7890-abcd-ef1234567890");

    @Mock PublicMapCache mapCache;

    @InjectMocks PublicMapResource resource;

    @Test
    void returnsMapPayload() {
        PublicMapResponse payload = new PublicMapResponse(GAME_ID, "Spring Rally", List.of(), List.of());
        when(mapCache.mapFor(GAME_ID)).thenReturn(Optional.of(payload));

        Response response = resource.map(GAME_ID);

        assertEquals(200, response.getStatus());
        assertEquals(payload, assertInstanceOf(PublicMapResponse.class, response.getEntity()));
    }

    @Test
    void unknownGame_returns404() {
        when(mapCache.mapFor(GAME_ID)).thenReturn(Optional.empty());

        Response response = resource.map(GAME_ID);

        assertEquals(404, response.getStatus());
    }
}
