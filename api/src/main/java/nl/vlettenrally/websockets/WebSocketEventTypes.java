package nl.vlettenrally.websockets;

/**
 * Constants for WebSocket message types
 * 
 * These should be kept in sync with the frontend WebSocketEventType
 */
public final class WebSocketEventTypes {
    
    private WebSocketEventTypes() {
        // Prevent instantiation
    }
    
    // Message events
    public static final String MESSAGE_NEW = "MESSAGE_NEW";
    
    // Game events
    public static final String COUNTDOWN_UPDATE = "COUNTDOWN_UPDATE";
    public static final String GAME_STATUS = "GAME_STATUS";
    
    // Team events
    public static final String TEAM_ACHIEVEMENT = "TEAM_ACHIEVEMENT";
    public static final String TEAM_CHECKIN = "TEAM_CHECKIN";
    public static final String TEAM_UPDATE = "TEAM_UPDATE";
    
    // Competition events
    public static final String RIVAL_UPDATE = "RIVAL_UPDATE";
    public static final String RANKING_UPDATE = "RANKING_UPDATE";
    
    // Admin events
    public static final String ADMIN_ALERT = "ADMIN_ALERT";
}
