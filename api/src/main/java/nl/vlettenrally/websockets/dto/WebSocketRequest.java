package nl.vlettenrally.websockets.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import lombok.Data;

/**
 * Base class for incoming WebSocket messages
 */
@JsonIgnoreProperties(ignoreUnknown = true)
@Data
public class WebSocketRequest {
    
    private WebSocketMessageType type;
    private String gameId;
    
    public WebSocketMessageType getType() {
        return type;
    }
}
