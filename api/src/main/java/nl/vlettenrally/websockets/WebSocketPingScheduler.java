package nl.vlettenrally.websockets;

import io.quarkus.logging.Log;
import io.quarkus.scheduler.Scheduled;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import org.eclipse.microprofile.config.inject.ConfigProperty;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.Collection;

/**
 * Scheduler service that sends periodic ping messages to all active WebSocket connections
 * to keep them alive through proxies, firewalls, and NAT devices.
 */
@ApplicationScoped
public class WebSocketPingScheduler {
    
    @Inject
    WebSocketSessionManager sessionManager;
    
    @ConfigProperty(name = "vlettenrally.websocket.ping-interval-seconds", defaultValue = "20")
    int pingIntervalSeconds;
    
    /**
     * Send ping messages to all active WebSocket sessions
     * 
     * Runs every 20 seconds (configured via application.properties)
     * Uses Jakarta WebSocket protocol-level ping frames
     */
    @Scheduled(every = "20s")
    void sendPings() {
        Collection<WebSocketSession> sessions = sessionManager.getAllSessions();
        
        if (sessions.isEmpty()) {
            return;
        }
        
        int successCount = 0;
        int failureCount = 0;
        
        for (WebSocketSession wsSession : sessions) {
            try {
                if (wsSession.getSession().isOpen()) {
                    // Send WebSocket protocol ping frame
                    wsSession.getSession().getBasicRemote().sendPing(ByteBuffer.allocate(0));
                    successCount++;
                } else {
                    // Session is closed, remove it
                    sessionManager.removeSession(wsSession.getSessionId());
                }
            } catch (IOException e) {
                failureCount++;
                Log.error("Failed to send ping to session " + wsSession.getSessionId() + ": " + e.getMessage());
                // Remove failed session
                sessionManager.removeSession(wsSession.getSessionId());
            } catch (Exception e) {
                failureCount++;
                Log.error("Unexpected error sending ping to session " + wsSession.getSessionId() + ": " + e.getMessage());
            }
        }
        
        if (successCount > 0 || failureCount > 0) {
            Log.info("WebSocket ping sent to " + successCount + " sessions, " + failureCount + " failures");
        }
    }
}
