package nl.vlettenrally.data.repository.mock;

import io.quarkus.arc.profile.IfBuildProfile;
import jakarta.annotation.PostConstruct;
import jakarta.enterprise.context.ApplicationScoped;
import nl.vlettenrally.data.domain.WebSocketRegistration;
import nl.vlettenrally.data.repository.WebSocketRegistrationRepository;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * Mock implementation of WebSocketRegistrationRepository with prefilled data.
 * Only active in dev and test profiles - replaced by PostgresWebSocketRegistrationRepository in prod.
 */
@IfBuildProfile(anyOf = {"dev", "test"})
@ApplicationScoped
public class MockWebSocketRegistrationRepository implements WebSocketRegistrationRepository {

    public static final UUID WEBSOCKET_WATERVLOOIEN_ID = UUID.fromString("wwww0001-0000-0000-0000-000000000001");

    private final Map<UUID, WebSocketRegistration> webSocketRegistrations = new ConcurrentHashMap<>();

    @PostConstruct
    void init() {
        initializeData();
    }

    public void initializeData() {
        webSocketRegistrations.clear();

        // Active WebSocket for Team Watervlooien
        WebSocketRegistration ws1 = new WebSocketRegistration();
        ws1.setId(WEBSOCKET_WATERVLOOIEN_ID);
        ws1.setDeviceRegistrationId(MockDeviceRegistrationRepository.DEVICE_WATERVLOOIEN_ID);
        ws1.setSessionId("mock-session-watervlooien-001");
        ws1.setConnectedAt(Instant.now().minusSeconds(1800)); // Connected 30 min ago
        ws1.setDisconnectedAt(null); // Still connected
        webSocketRegistrations.put(ws1.getId(), ws1);

        // Disconnected WebSocket for Team Windkracht Tien (no active connection)
        WebSocketRegistration ws2 = new WebSocketRegistration();
        ws2.setId(UUID.fromString("wwww0002-0000-0000-0000-000000000002"));
        ws2.setDeviceRegistrationId(MockDeviceRegistrationRepository.DEVICE_WINDKRACHT_ID);
        ws2.setSessionId("mock-session-windkracht-001");
        ws2.setConnectedAt(Instant.now().minusSeconds(3600)); // Connected 1 hour ago
        ws2.setDisconnectedAt(Instant.now().minusSeconds(600)); // Disconnected 10 min ago
        webSocketRegistrations.put(ws2.getId(), ws2);
    }

    @Override
    public Optional<WebSocketRegistration> findById(UUID id) {
        return Optional.ofNullable(webSocketRegistrations.get(id));
    }

    @Override
    public List<WebSocketRegistration> findAll() {
        return new ArrayList<>(webSocketRegistrations.values());
    }

    @Override
    public Optional<WebSocketRegistration> findBySessionId(String sessionId) {
        return webSocketRegistrations.values().stream()
                .filter(ws -> ws.getSessionId().equals(sessionId))
                .findFirst();
    }

    @Override
    public List<WebSocketRegistration> findByDeviceRegistrationId(UUID deviceRegistrationId) {
        return webSocketRegistrations.values().stream()
                .filter(ws -> ws.getDeviceRegistrationId().equals(deviceRegistrationId))
                .collect(Collectors.toList());
    }

    @Override
    public List<WebSocketRegistration> findActiveByDeviceRegistrationId(UUID deviceRegistrationId) {
        return webSocketRegistrations.values().stream()
                .filter(ws -> ws.getDeviceRegistrationId().equals(deviceRegistrationId) && ws.isConnected())
                .collect(Collectors.toList());
    }

    @Override
    public WebSocketRegistration save(WebSocketRegistration webSocketRegistration) {
        if (webSocketRegistration.getId() == null) {
            webSocketRegistration.setId(UUID.randomUUID());
        }
        webSocketRegistrations.put(webSocketRegistration.getId(), webSocketRegistration);
        return webSocketRegistration;
    }

    @Override
    public void markDisconnected(String sessionId) {
        findBySessionId(sessionId).ifPresent(WebSocketRegistration::disconnect);
    }

    @Override
    public void delete(UUID id) {
        webSocketRegistrations.remove(id);
    }
}
