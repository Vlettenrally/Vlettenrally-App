package nl.vlettenrally.websockets;

import java.time.Duration;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import org.mockito.junit.jupiter.MockitoExtension;

import com.fasterxml.jackson.databind.ObjectMapper;

import io.smallrye.mutiny.Uni;
import jakarta.websocket.RemoteEndpoint;
import jakarta.websocket.Session;
import nl.vlettenrally.websockets.dto.WebSocketResponse;

/**
 * Unit tests for WebSocketPingHandler.
 * Tests cross-pod PING/PONG handling with Valkey fallback.
 */
@ExtendWith(MockitoExtension.class)
class WebSocketPingHandlerTest {

    @Mock
    WebSocketSessionManager sessionManager;

    @Mock
    ValkeySessionStore valkeySessionStore;

    @InjectMocks
    WebSocketPingHandler pingHandler;

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final UUID gameId = UUID.randomUUID();
    private final UUID teamId = UUID.randomUUID();
    private final String sessionId = "test-session-123";

    private Session mockSession;
    private RemoteEndpoint.Async asyncRemote;

    @BeforeEach
    void setUp() {
        pingHandler.objectMapper = objectMapper;
        
        mockSession = mock(Session.class);
        asyncRemote = mock(RemoteEndpoint.Async.class);
    }

    @Test
    void handlePing_sessionRegisteredLocally_sendsPongDirectly() throws Exception {
        // Session exists in local manager
        when(mockSession.getId()).thenReturn(sessionId);
        when(mockSession.getAsyncRemote()).thenReturn(asyncRemote);
        WebSocketSession wsSession = WebSocketSession.forTeam(mockSession, gameId, teamId);
        when(sessionManager.getSession(sessionId)).thenReturn(wsSession);
        when(valkeySessionStore.updateActivity(sessionId)).thenReturn(Uni.createFrom().voidItem());

        pingHandler.handlePing(mockSession);

        // Should update activity in Valkey
        verify(valkeySessionStore).updateActivity(sessionId);
        
        // Should send PONG
        ArgumentCaptor<String> responseCaptor = ArgumentCaptor.forClass(String.class);
        verify(asyncRemote).sendText(responseCaptor.capture());
        
        String response = responseCaptor.getValue();
        assertNotNull(response);
        // Check that response contains PONG
        assertTrue(response.contains("PONG"));
    }

    @Test
    void handlePing_sessionInValkeyOnly_registersLocallyAndSendsPong() throws Exception {
        // Session NOT in local manager
        when(mockSession.getId()).thenReturn(sessionId);
        when(mockSession.getAsyncRemote()).thenReturn(asyncRemote);
        when(sessionManager.getSession(sessionId)).thenReturn(null);
        
        // But exists in Valkey
        WebSocketSessionMetadata metadata = WebSocketSessionMetadata.forTeam(sessionId, gameId, teamId);
        when(valkeySessionStore.getSession(sessionId)).thenReturn(Uni.createFrom().item(metadata));
        when(valkeySessionStore.updateActivity(sessionId)).thenReturn(Uni.createFrom().voidItem());

        pingHandler.handlePing(mockSession);

        // Wait for async operations
        Thread.sleep(100);

        // Should register session locally
        ArgumentCaptor<WebSocketSession> sessionCaptor = ArgumentCaptor.forClass(WebSocketSession.class);
        verify(sessionManager).addSession(sessionCaptor.capture());
        
        WebSocketSession registered = sessionCaptor.getValue();
        assertNotNull(registered);
        assertEquals(gameId, registered.getGameId());
        assertEquals(teamId, registered.getTeamId());

        // Should update activity in Valkey
        verify(valkeySessionStore).updateActivity(sessionId);
        
        // Should send PONG
        verify(asyncRemote).sendText(anyString());
    }

    @Test
    void handlePing_sessionNotFound_doesNotSendPong() throws Exception {
        // Session NOT in local manager
        when(mockSession.getId()).thenReturn(sessionId);
        when(sessionManager.getSession(sessionId)).thenReturn(null);
        
        // And NOT in Valkey
        when(valkeySessionStore.getSession(sessionId)).thenReturn(Uni.createFrom().nullItem());

        pingHandler.handlePing(mockSession);

        // Wait for async operations
        Thread.sleep(100);

        // Should NOT register session locally
        verify(sessionManager, never()).addSession(any(WebSocketSession.class));
        
        // Should NOT update activity
        verify(valkeySessionStore, never()).updateActivity(anyString());
        
        // Should NOT send PONG
        verify(asyncRemote, never()).sendText(anyString());
    }

    @Test
    void handlePing_valkeyError_doesNotSendPong() throws Exception {
        // Session NOT in local manager
        when(mockSession.getId()).thenReturn(sessionId);
        when(sessionManager.getSession(sessionId)).thenReturn(null);
        
        // Valkey throws error
        when(valkeySessionStore.getSession(sessionId))
            .thenReturn(Uni.createFrom().failure(new RuntimeException("Valkey error")));

        pingHandler.handlePing(mockSession);

        // Wait for async operations
        Thread.sleep(100);

        // Should NOT register session locally
        verify(sessionManager, never()).addSession(any(WebSocketSession.class));
        
        // Should NOT send PONG
        verify(asyncRemote, never()).sendText(anyString());
    }

    @Test
    void handlePing_adminSessionInValkey_registersAndSendsPong() throws Exception {
        // Session NOT in local manager
        when(mockSession.getId()).thenReturn(sessionId);
        when(mockSession.getAsyncRemote()).thenReturn(asyncRemote);
        when(sessionManager.getSession(sessionId)).thenReturn(null);
        
        // Admin session exists in Valkey
        WebSocketSessionMetadata metadata = WebSocketSessionMetadata.forAdmin(sessionId, gameId, UUID.randomUUID());
        when(valkeySessionStore.getSession(sessionId)).thenReturn(Uni.createFrom().item(metadata));
        when(valkeySessionStore.updateActivity(sessionId)).thenReturn(Uni.createFrom().voidItem());

        pingHandler.handlePing(mockSession);

        // Wait for async operations
        Thread.sleep(100);

        // Should register admin session locally
        ArgumentCaptor<WebSocketSession> sessionCaptor = ArgumentCaptor.forClass(WebSocketSession.class);
        verify(sessionManager).addSession(sessionCaptor.capture());
        
        WebSocketSession registered = sessionCaptor.getValue();
        assertNotNull(registered);
        assertEquals(gameId, registered.getGameId());

        // Should send PONG
        verify(asyncRemote).sendText(anyString());
    }

    @Test
    void handlePing_adminSessionNoGame_registersAndSendsPong() throws Exception {
        // Session NOT in local manager
        when(mockSession.getId()).thenReturn(sessionId);
        when(mockSession.getAsyncRemote()).thenReturn(asyncRemote);
        when(sessionManager.getSession(sessionId)).thenReturn(null);
        
        // Admin session with no game exists in Valkey
        WebSocketSessionMetadata metadata = WebSocketSessionMetadata.forAdminNoGame(sessionId, UUID.randomUUID());
        when(valkeySessionStore.getSession(sessionId)).thenReturn(Uni.createFrom().item(metadata));
        when(valkeySessionStore.updateActivity(sessionId)).thenReturn(Uni.createFrom().voidItem());

        pingHandler.handlePing(mockSession);

        // Wait for async operations
        Thread.sleep(100);

        // Should register admin session locally
        verify(sessionManager).addSession(any(WebSocketSession.class));

        // Should send PONG
        verify(asyncRemote).sendText(anyString());
    }
}
