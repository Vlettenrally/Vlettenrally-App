package nl.vlettenrally.websockets;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import org.mockito.junit.jupiter.MockitoExtension;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;

import io.quarkus.redis.datasource.ReactiveRedisDataSource;
import io.quarkus.redis.datasource.value.ReactiveValueCommands;
import io.smallrye.mutiny.Uni;
import nl.vlettenrally.data.domain.Team;
import nl.vlettenrally.data.repository.TeamRepository;

/**
 * Unit tests for WebSocketTokenService.
 * Tests token generation, validation, and consumption.
 */
@ExtendWith(MockitoExtension.class)
class WebSocketTokenServiceTest {

    @Mock
    ReactiveRedisDataSource redisDataSource;

    @Mock
    ReactiveValueCommands<String, String> valueCommands;

    @Mock
    TeamRepository teamRepository;

    @InjectMocks
    WebSocketTokenService tokenService;

    private ObjectMapper objectMapper;

    private final UUID adminId = UUID.randomUUID();
    private final UUID teamId = UUID.randomUUID();
    private final UUID gameId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        objectMapper.registerModule(new JavaTimeModule());
        tokenService.objectMapper = objectMapper;
    }

    @Test
    void generateAdminToken_storesTokenInValkey() {
        when(redisDataSource.value(String.class)).thenReturn(valueCommands);
        when(valueCommands.setex(anyString(), anyLong(), anyString())).thenReturn(Uni.createFrom().voidItem());

        String token = tokenService.generateAdminToken(adminId, "Test Admin")
            .await().atMost(Duration.ofSeconds(2));

        assertNotNull(token);
        assertTrue(token.length() > 20); // Base64 encoded 32 bytes
        verify(valueCommands).setex(eq("ws:token:" + token), eq(30L), anyString());
    }

    @Test
    void generateTeamToken_withValidTeam_storesTokenInValkey() {
        Team team = createTestTeam();
        when(teamRepository.findById(teamId)).thenReturn(Optional.of(team));
        when(redisDataSource.value(String.class)).thenReturn(valueCommands);
        when(valueCommands.setex(anyString(), anyLong(), anyString())).thenReturn(Uni.createFrom().voidItem());

        WebSocketTokenResult result = tokenService.generateTeamToken(teamId, gameId)
            .await().atMost(Duration.ofSeconds(2));

        assertTrue(result instanceof WebSocketTokenResult.Success);
        WebSocketTokenResult.Success success = (WebSocketTokenResult.Success) result;
        assertNotNull(success.token());
        assertEquals(30, success.expiresInSeconds());
    }

    @Test
    void generateTeamToken_teamNotFound_returnsTeamNotFound() {
        when(teamRepository.findById(teamId)).thenReturn(Optional.empty());

        WebSocketTokenResult result = tokenService.generateTeamToken(teamId, gameId)
            .await().atMost(Duration.ofSeconds(2));

        assertTrue(result instanceof WebSocketTokenResult.TeamNotFound);
        verify(redisDataSource, never()).value(String.class);
    }

    @Test
    void generateTeamToken_teamGameMismatch_returnsTeamGameMismatch() {
        Team team = createTestTeam();
        team.setGameId(UUID.randomUUID()); // Different game
        when(teamRepository.findById(teamId)).thenReturn(Optional.of(team));

        WebSocketTokenResult result = tokenService.generateTeamToken(teamId, gameId)
            .await().atMost(Duration.ofSeconds(2));

        assertTrue(result instanceof WebSocketTokenResult.TeamGameMismatch);
        verify(redisDataSource, never()).value(String.class);
    }

    @Test
    void validateAndConsumeToken_validToken_returnsDataAndDeletes() throws Exception {
        // Build JSON manually to match expected format
        String json = String.format(
            "{\"role\":\"admin\",\"userId\":\"%s\",\"userName\":\"Test Admin\",\"gameId\":null,\"teamId\":null,\"createdAt\":\"%s\"}",
            adminId, Instant.now().toString()
        );

        when(redisDataSource.value(String.class)).thenReturn(valueCommands);
        when(valueCommands.getdel("ws:token:test-token")).thenReturn(Uni.createFrom().item(json));

        WebSocketTokenData result = tokenService.validateAndConsumeToken("test-token")
            .await().atMost(Duration.ofSeconds(2));

        assertNotNull(result);
        assertEquals("admin", result.role());
        assertEquals(adminId, result.userId());
        assertEquals("Test Admin", result.userName());
    }

    @Test
    void validateAndConsumeToken_expiredOrInvalidToken_returnsNull() {
        when(redisDataSource.value(String.class)).thenReturn(valueCommands);
        when(valueCommands.getdel("ws:token:invalid-token")).thenReturn(Uni.createFrom().nullItem());

        WebSocketTokenData result = tokenService.validateAndConsumeToken("invalid-token")
            .await().atMost(Duration.ofSeconds(2));

        assertNull(result);
    }

    @Test
    void validateAndConsumeToken_nullOrBlankToken_returnsNull() {
        WebSocketTokenData resultNull = tokenService.validateAndConsumeToken(null)
            .await().atMost(Duration.ofSeconds(2));
        WebSocketTokenData resultBlank = tokenService.validateAndConsumeToken("   ")
            .await().atMost(Duration.ofSeconds(2));

        assertNull(resultNull);
        assertNull(resultBlank);
        verify(redisDataSource, never()).value(String.class);
    }

    @Test
    void validateToken_validToken_returnsDataWithoutDeleting() throws Exception {
        // Build JSON manually to match expected format
        String json = String.format(
            "{\"role\":\"team\",\"userId\":\"%s\",\"userName\":\"Test Team\",\"gameId\":\"%s\",\"teamId\":\"%s\",\"createdAt\":\"%s\"}",
            teamId, gameId, teamId, Instant.now().toString()
        );

        when(redisDataSource.value(String.class)).thenReturn(valueCommands);
        when(valueCommands.get("ws:token:test-token")).thenReturn(Uni.createFrom().item(json));

        WebSocketTokenData result = tokenService.validateToken("test-token")
            .await().atMost(Duration.ofSeconds(2));

        assertNotNull(result);
        assertEquals("team", result.role());
        assertEquals(teamId, result.userId());
        assertEquals(gameId, result.gameId());

        // Should use get, not getdel
        verify(valueCommands).get("ws:token:test-token");
        verify(valueCommands, never()).getdel(anyString());
    }

    @Test
    void validateAndConsumeToken_valkeyError_returnsNull() {
        when(redisDataSource.value(String.class)).thenReturn(valueCommands);
        when(valueCommands.getdel("ws:token:test-token"))
            .thenReturn(Uni.createFrom().failure(new RuntimeException("Connection failed")));

        WebSocketTokenData result = tokenService.validateAndConsumeToken("test-token")
            .await().atMost(Duration.ofSeconds(2));

        assertNull(result);
    }

    @Test
    void validateAndConsumeToken_malformedJson_returnsNull() {
        when(redisDataSource.value(String.class)).thenReturn(valueCommands);
        when(valueCommands.getdel("ws:token:test-token"))
            .thenReturn(Uni.createFrom().item("not valid json"));

        WebSocketTokenData result = tokenService.validateAndConsumeToken("test-token")
            .await().atMost(Duration.ofSeconds(2));

        assertNull(result);
    }

    private Team createTestTeam() {
        Team team = new Team();
        team.setId(teamId);
        team.setGameId(gameId);
        team.setName("Test Team");
        team.setBoatNumber("42");
        return team;
    }
}









