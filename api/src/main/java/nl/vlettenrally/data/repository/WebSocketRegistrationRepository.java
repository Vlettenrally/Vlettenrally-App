package nl.vlettenrally.data.repository;

import nl.vlettenrally.data.domain.WebSocketRegistration;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Repository interface for WebSocketRegistration entities
 */
public interface WebSocketRegistrationRepository {
    
    Optional<WebSocketRegistration> findById(UUID id);
    
    List<WebSocketRegistration> findAll();
    
    Optional<WebSocketRegistration> findBySessionId(String sessionId);
    
    List<WebSocketRegistration> findByDeviceRegistrationId(UUID deviceRegistrationId);
    
    /**
     * Find all active (connected) WebSocket registrations for a device
     */
    List<WebSocketRegistration> findActiveByDeviceRegistrationId(UUID deviceRegistrationId);
    
    WebSocketRegistration save(WebSocketRegistration webSocketRegistration);
    
    void markDisconnected(String sessionId);
    
    void delete(UUID id);
}
