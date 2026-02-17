package nl.vlettenrally.websockets;

import java.io.IOException;
import java.time.Duration;
import java.time.Instant;
import java.util.Collection;

import io.quarkus.logging.Log;
import io.quarkus.scheduler.Scheduled;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

/**
 * Scheduler service that cleans up stale WebSocket connections.
 * Sessions that haven't received a PING within 30 seconds are considered stale and closed.
 */
@ApplicationScoped
public class WebSocketPingScheduler {
    
    private static final Duration STALE_THRESHOLD = Duration.ofSeconds(30);
    
    @Inject
    WebSocketSessionManager sessionManager;
    
    /**
     * Check for stale WebSocket sessions and close them.
     * 
     * Runs every 10 seconds. Sessions are considered stale if they haven't
     * received a PING message within 30 seconds.
     */
    @Scheduled(every = "10s")
    public void cleanupStaleSessions() {
        Collection<WebSocketSession> sessions = sessionManager.getAllSessions();
        
        if (sessions.isEmpty()) {
            return;
        }
        
        Instant threshold = Instant.now().minus(STALE_THRESHOLD);
        int closedCount = 0;
        
        for (WebSocketSession wsSession : sessions) {
            try {
                if (!wsSession.getSession().isOpen()) {
                    // Session is already closed, remove it
                    sessionManager.removeSession(wsSession.getSessionId());
                    closedCount++;
                } else if (wsSession.getLastActivity().isBefore(threshold)) {
                    // Session is stale (no PING received within threshold)
                    Log.infof("Closing stale WebSocket session %s (last activity: %s)", 
                             wsSession.getSessionId(), wsSession.getLastActivity());
                    wsSession.getSession().close();
                    sessionManager.removeSession(wsSession.getSessionId());
                    closedCount++;
                }
            } catch (IOException | IllegalStateException e) {
                Log.errorf("Failed to close session %s: %s", wsSession.getSessionId(), e.getMessage());
                sessionManager.removeSession(wsSession.getSessionId());
                closedCount++;
            }
        }
        
        if (closedCount > 0) {
            Log.infof("WebSocket cleanup: closed %d stale sessions", closedCount);
        }
    }
}
