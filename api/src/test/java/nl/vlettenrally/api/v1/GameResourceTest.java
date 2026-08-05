package nl.vlettenrally.api.v1;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.util.List;

import nl.vlettenrally.data.domain.Game;
import nl.vlettenrally.data.dto.common.ErrorResponse;
import nl.vlettenrally.data.dto.game.GameResponse;
import nl.vlettenrally.data.dto.game.RankingResponse;
import nl.vlettenrally.data.dto.game.TeamImportResponse;
import nl.vlettenrally.service.GameService;
import nl.vlettenrally.service.TeamImportResult;
import nl.vlettenrally.service.RankingService;
import nl.vlettenrally.service.TeamImportService;
import nl.vlettenrally.service.TrackerAssignmentService;
import nl.vlettenrally.service.TrackerAssignmentService.ExportColumn;
import nl.vlettenrally.service.TrackerAssignmentService.ExportField;
import nl.vlettenrally.websockets.WebSocketPublisher;
import nl.vlettenrally.websockets.dto.WebSocketMessageType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.SecurityContext;
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

    @Mock
    RankingService rankingService;

    @Mock
    WebSocketPublisher webSocketPublisher;

    @Mock
    TrackerAssignmentService trackerAssignmentService;

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
        when(teamImportService.importTeams(gameId, 42)).thenReturn(
            new TeamImportResult(3, 1, 0, 0, List.of(), List.of(), List.of(), List.of()));

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
        when(teamImportService.importTeams(gameId, 42)).thenReturn(
            new TeamImportResult(0, 2, 0, 0, List.of(), List.of(), List.of(), List.of()));

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

    // ---- rankings visibility ----------------------------------------------

    private SecurityContext ctxWithRole(boolean admin) {
        SecurityContext ctx = mock(SecurityContext.class);
        when(ctx.isUserInRole(nl.vlettenrally.api.security.SecurityRoles.STAFF)).thenReturn(admin);
        return ctx;
    }

    @Test
    void getRankings_teamBeforeReveal_returnsForbidden() throws Exception {
        Game game = gameWith(Game.GameStatus.COMPLETED, 42); // not published
        when(gameService.findById(gameId)).thenReturn(Optional.of(game));

        try (Response response = gameResource.getRankings(gameId, ctxWithRole(false))) {
            assertEquals(Response.Status.FORBIDDEN.getStatusCode(), response.getStatus());
            verify(rankingService, never()).buildRanking(any());
        }
    }

    @Test
    void getRankings_teamAfterReveal_returnsOk() throws Exception {
        Game game = gameWith(Game.GameStatus.COMPLETED, 42);
        game.setRankingsPublished(true);
        when(gameService.findById(gameId)).thenReturn(Optional.of(game));
        when(rankingService.buildRanking(gameId))
            .thenReturn(java.util.List.of(new RankingResponse(1, UUID.randomUUID(), "Team", null, 10)));

        try (Response response = gameResource.getRankings(gameId, ctxWithRole(false))) {
            assertEquals(Response.Status.OK.getStatusCode(), response.getStatus());
            verify(rankingService).buildRanking(gameId);
        }
    }

    @Test
    void getRankings_adminBeforeReveal_returnsOk() throws Exception {
        Game game = gameWith(Game.GameStatus.ACTIVE, 42); // not published, mid-game
        when(gameService.findById(gameId)).thenReturn(Optional.of(game));
        when(rankingService.buildRanking(gameId)).thenReturn(java.util.List.of());

        try (Response response = gameResource.getRankings(gameId, ctxWithRole(true))) {
            assertEquals(Response.Status.OK.getStatusCode(), response.getStatus());
            verify(rankingService).buildRanking(gameId);
        }
    }

    @Test
    void exportRankings_returnsCsvAsAttachment() throws Exception {
        when(gameService.findById(gameId)).thenReturn(Optional.of(gameWith(Game.GameStatus.COMPLETED, 42)));
        when(rankingService.exportCsv(gameId)).thenReturn("plaats;bootnummer\n1;Boot 1\n");

        try (Response response = gameResource.exportRankings(gameId)) {
            assertEquals(Response.Status.OK.getStatusCode(), response.getStatus());
            assertEquals("plaats;bootnummer\n1;Boot 1\n", response.getEntity());
            String disposition = response.getHeaderString("Content-Disposition");
            assertTrue(disposition.startsWith("attachment;"), disposition);
            assertTrue(disposition.contains("ranglijst-" + gameId + ".csv"), disposition);
        }
    }

    @Test
    void exportRankings_gameNotFound_returnsBodilessNotFound() throws Exception {
        when(gameService.findById(gameId)).thenReturn(Optional.empty());

        try (Response response = gameResource.exportRankings(gameId)) {
            assertEquals(Response.Status.NOT_FOUND.getStatusCode(), response.getStatus());
            // A JSON error body would have no writer under this method's text/csv producer.
            assertNull(response.getEntity());
            verify(rankingService, never()).exportCsv(any());
        }
    }

    @Test
    void publishRankings_setsFlag_savesAndBroadcasts() throws Exception {
        Game game = gameWith(Game.GameStatus.COMPLETED, 42);
        when(gameService.findById(gameId)).thenReturn(Optional.of(game));
        when(gameService.save(any(Game.class))).thenAnswer(inv -> inv.getArgument(0));
        when(rankingService.buildRanking(gameId)).thenReturn(java.util.List.of());

        try (Response response = gameResource.publishRankings(gameId)) {
            assertEquals(Response.Status.OK.getStatusCode(), response.getStatus());
            assertTrue(game.getRankingsPublished());
            GameResponse dto = (GameResponse) response.getEntity();
            assertTrue(dto.rankingsPublished());
            verify(gameService).save(game);
            verify(webSocketPublisher).broadcastToGame(eq(gameId),
                eq(WebSocketMessageType.RANKING_UPDATE.getValue()), any());
        }
    }

    @Test
    void publishRankings_gameNotFound_returnsNotFound() throws Exception {
        when(gameService.findById(gameId)).thenReturn(Optional.empty());

        try (Response response = gameResource.publishRankings(gameId)) {
            assertEquals(Response.Status.NOT_FOUND.getStatusCode(), response.getStatus());
            verify(webSocketPublisher, never()).broadcastToGame(any(), any(), any());
        }
    }

    @Test
    void exportTrackers_noFields_usesDefaultColumns() throws Exception {
        when(gameService.findById(gameId)).thenReturn(Optional.of(gameWith(Game.GameStatus.ACTIVE, 42)));
        when(trackerAssignmentService.exportCsv(gameId, TrackerAssignmentService.DEFAULT_COLUMNS, false))
            .thenReturn("csv");

        try (Response response = gameResource.exportTrackers(gameId, List.of(), List.of(), false)) {
            assertEquals(Response.Status.OK.getStatusCode(), response.getStatus());
            assertEquals("csv", response.getEntity());
        }
    }

    @Test
    void exportTrackers_pairsHeadersByIndex_andFallsBackWhenBlank() throws Exception {
        when(gameService.findById(gameId)).thenReturn(Optional.of(gameWith(Game.GameStatus.ACTIVE, 42)));
        List<ExportColumn> expected = List.of(
            new ExportColumn(ExportField.NAME, "Boot"),
            new ExportColumn(ExportField.TRACKER_ID, "trackerId"));
        when(trackerAssignmentService.exportCsv(gameId, expected, true)).thenReturn("csv");

        try (Response response = gameResource.exportTrackers(
                gameId, List.of("name", "trackerId"), List.of("Boot", " "), true)) {
            assertEquals(Response.Status.OK.getStatusCode(), response.getStatus());
            verify(trackerAssignmentService).exportCsv(gameId, expected, true);
        }
    }

    @Test
    void exportTrackers_unknownField_returnsBadRequest() throws Exception {
        when(gameService.findById(gameId)).thenReturn(Optional.of(gameWith(Game.GameStatus.ACTIVE, 42)));

        try (Response response = gameResource.exportTrackers(
                gameId, List.of("notes"), List.of(), false)) {
            assertEquals(Response.Status.BAD_REQUEST.getStatusCode(), response.getStatus());
            assertTrue(((ErrorResponse) response.getEntity()).message().contains("notes"));
            verify(trackerAssignmentService, never()).exportCsv(any(), any(), anyBoolean());
        }
    }
}
