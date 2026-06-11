package nl.vlettenrally.api.v1;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import nl.vlettenrally.data.domain.Game;
import nl.vlettenrally.data.dto.common.ErrorResponse;
import nl.vlettenrally.data.dto.game.TeamImportResponse;
import nl.vlettenrally.service.GameService;
import nl.vlettenrally.service.ImportResult;
import nl.vlettenrally.service.TeamImportService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import jakarta.ws.rs.core.Response;
import java.util.Optional;
import java.util.UUID;

/**
 * Unit tests for GameResource.importTeams.
 * Team import is allowed at any time except when the game is COMPLETED.
 */
@ExtendWith(MockitoExtension.class)
class GameResourceTest {

    @Mock
    GameService gameService;

    @Mock
    TeamImportService teamImportService;

    @InjectMocks
    GameResource gameResource;

    private UUID gameId;

    @BeforeEach
    void setUp() {
        gameId = UUID.randomUUID();
    }

    private Game gameWith(Game.GameStatus status, Integer externalEventId) {
        Game game = new Game();
        game.setId(gameId);
        game.setStatus(status);
        game.setExternalEventId(externalEventId);
        return game;
    }

    @Test
    void importTeams_plannedGame_returnsOk() throws Exception {
        Game game = gameWith(Game.GameStatus.PLANNED, 42);
        when(gameService.findById(gameId)).thenReturn(Optional.of(game));
        when(teamImportService.importTeams(gameId, 42)).thenReturn(new ImportResult(3, 1, 0));

        try (Response response = gameResource.importTeams(gameId)) {
            assertEquals(Response.Status.OK.getStatusCode(), response.getStatus());
            TeamImportResponse entity = (TeamImportResponse) response.getEntity();
            assertEquals(3, entity.created());
            verify(teamImportService).importTeams(gameId, 42);
        }
    }

    @Test
    void importTeams_activeGame_returnsOk() throws Exception {
        Game game = gameWith(Game.GameStatus.ACTIVE, 42);
        when(gameService.findById(gameId)).thenReturn(Optional.of(game));
        when(teamImportService.importTeams(gameId, 42)).thenReturn(new ImportResult(0, 2, 0));

        try (Response response = gameResource.importTeams(gameId)) {
            assertEquals(Response.Status.OK.getStatusCode(), response.getStatus());
            verify(teamImportService).importTeams(gameId, 42);
        }
    }

    @Test
    void importTeams_completedGame_returnsConflict() throws Exception {
        Game game = gameWith(Game.GameStatus.COMPLETED, 42);
        when(gameService.findById(gameId)).thenReturn(Optional.of(game));

        try (Response response = gameResource.importTeams(gameId)) {
            assertEquals(Response.Status.CONFLICT.getStatusCode(), response.getStatus());
            ErrorResponse error = (ErrorResponse) response.getEntity();
            assertTrue(error.message().toLowerCase().contains("completed"));
            verify(teamImportService, never()).importTeams(any(), anyInt());
        }
    }

    @Test
    void importTeams_noExternalEvent_returnsBadRequest() throws Exception {
        Game game = gameWith(Game.GameStatus.PLANNED, null);
        when(gameService.findById(gameId)).thenReturn(Optional.of(game));

        try (Response response = gameResource.importTeams(gameId)) {
            assertEquals(Response.Status.BAD_REQUEST.getStatusCode(), response.getStatus());
            verify(teamImportService, never()).importTeams(any(), anyInt());
        }
    }

    @Test
    void importTeams_gameNotFound_returnsNotFound() throws Exception {
        when(gameService.findById(gameId)).thenReturn(Optional.empty());

        try (Response response = gameResource.importTeams(gameId)) {
            assertEquals(Response.Status.NOT_FOUND.getStatusCode(), response.getStatus());
            verify(teamImportService, never()).importTeams(any(), anyInt());
        }
    }
}
