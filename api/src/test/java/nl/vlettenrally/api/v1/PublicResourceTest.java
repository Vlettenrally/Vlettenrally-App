package nl.vlettenrally.api.v1;

import nl.vlettenrally.data.domain.Game;
import nl.vlettenrally.data.dto.game.PublicGameResponse;
import nl.vlettenrally.service.GameService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import jakarta.ws.rs.core.Response;

import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link PublicResource}: the shared public game listing projection.
 */
@ExtendWith(MockitoExtension.class)
class PublicResourceTest {

    @Mock GameService gameService;

    @InjectMocks PublicResource resource;

    @Test
    void games_returnsMinimalProjectionMostRecentFirst() {
        Game older = game("Spring Rally", Instant.parse("2026-04-01T10:00:00Z"));
        Game newer = game("Summer Rally", Instant.parse("2026-06-01T10:00:00Z"));
        when(gameService.findAll()).thenReturn(List.of(older, newer));

        Response response = resource.games();

        assertEquals(200, response.getStatus());
        List<?> body = assertInstanceOf(List.class, response.getEntity());
        assertEquals(2, body.size());
        PublicGameResponse first = (PublicGameResponse) body.getFirst();
        assertEquals("Summer Rally", first.name());
        assertEquals("planned", first.status());
        assertFalse(first.scoresPublished());
    }

    private Game game(String name, Instant startTime) {
        Game g = new Game();
        g.setName(name);
        g.setStartTime(startTime);
        return g;
    }
}
