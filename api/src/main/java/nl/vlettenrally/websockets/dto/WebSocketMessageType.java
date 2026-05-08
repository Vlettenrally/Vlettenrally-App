package nl.vlettenrally.websockets.dto;

import com.fasterxml.jackson.annotation.JsonValue;

/**
 * Enum for WebSocket message types
 */
public enum WebSocketMessageType {
    
    // Client -> Server messages
    PING("PING"),
    SWITCH_GAME("SWITCH_GAME"),
    MAP_HEARTBEAT("MAP_HEARTBEAT"),
    CHECKPOINT_HEARTBEAT("CHECKPOINT_HEARTBEAT"),
    CHECKPOINT_SUBSCRIBE("CHECKPOINT_SUBSCRIBE"),
    CHECKPOINT_UNSUBSCRIBE("CHECKPOINT_UNSUBSCRIBE"),

    // Server -> Client messages
    PONG("PONG"),
    GAME_SWITCHED("GAME_SWITCHED"),
    ERROR("ERROR"),
    
    // Broadcast messages
    MESSAGE_NEW("MESSAGE_NEW"),
    MESSAGE_DELIVERY_UPDATE("MESSAGE_DELIVERY_UPDATE"),
    COUNTDOWN_UPDATE("COUNTDOWN_UPDATE"),
    TEAM_ACHIEVEMENT("TEAM_ACHIEVEMENT"),
    RIVAL_UPDATE("RIVAL_UPDATE"),
    RANKING_UPDATE("RANKING_UPDATE"),
    GAME_STATUS("GAME_STATUS"),
    ADMIN_ALERT("ADMIN_ALERT"),
    TEAM_CHECKIN("TEAM_CHECKIN"),
    TEAM_UPDATE("TEAM_UPDATE"),
    LOCATION_UPDATE("LOCATION_UPDATE"),
    CHECKPOINT_DETECTION("CHECKPOINT_DETECTION");

    private final String value;
    
    WebSocketMessageType(String value) {
        this.value = value;
    }
    
    @JsonValue
    public String getValue() {
        return value;
    }
}
