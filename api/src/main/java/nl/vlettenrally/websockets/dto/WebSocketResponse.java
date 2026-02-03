package nl.vlettenrally.websockets.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

import lombok.Data;

/**
 * Base class for outgoing WebSocket messages
 */
@Data
@JsonInclude(JsonInclude.Include.NON_NULL)
public class WebSocketResponse {
    
    private final WebSocketMessageType type;
    private String gameId;
    private String message;
    
    public WebSocketResponse(WebSocketMessageType type) {
        this.type = type;
    }
    
    public static WebSocketResponse pong() {
        return new WebSocketResponse(WebSocketMessageType.PONG);
    }
    
    public static WebSocketResponse gameSwitched(String gameId) {
        WebSocketResponse response = new WebSocketResponse(WebSocketMessageType.GAME_SWITCHED);
        response.setGameId(gameId);
        return response;
    }
    
    public static WebSocketResponse error(String message) {
        WebSocketResponse response = new WebSocketResponse(WebSocketMessageType.ERROR);
        response.setMessage(message);
        return response;
    }
}
