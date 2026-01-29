package nl.vlettenrally.data.repository.mock;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.Priority;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Alternative;
import jakarta.inject.Inject;
import nl.vlettenrally.data.domain.DeviceRegistration;
import nl.vlettenrally.data.domain.FcmToken;
import nl.vlettenrally.data.repository.DeviceRegistrationRepository;
import nl.vlettenrally.data.repository.FcmTokenRepository;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * Mock implementation of FcmTokenRepository with prefilled data.
 * Enabled by default in dev mode via @Priority.
 */
@Alternative
@Priority(1)
@ApplicationScoped
public class MockFcmTokenRepository implements FcmTokenRepository {

    public static final UUID FCM_TOKEN_WATERVLOOIEN_ID = UUID.fromString("ffff0001-0000-0000-0000-000000000001");
    public static final UUID FCM_TOKEN_WINDKRACHT_ID = UUID.fromString("ffff0002-0000-0000-0000-000000000002");

    @Inject
    DeviceRegistrationRepository deviceRegistrationRepository;

    private final Map<UUID, FcmToken> fcmTokens = new ConcurrentHashMap<>();

    @PostConstruct
    void init() {
        initializeData();
    }

    public void initializeData() {
        fcmTokens.clear();

        // FCM token for Team Watervlooien's device
        FcmToken token1 = new FcmToken();
        token1.setId(FCM_TOKEN_WATERVLOOIEN_ID);
        token1.setDeviceRegistrationId(MockDeviceRegistrationRepository.DEVICE_WATERVLOOIEN_ID);
        token1.setToken("mock-fcm-token-watervlooien-abc123");
        token1.setCreatedAt(Instant.now().minusSeconds(3600));
        token1.setUpdatedAt(Instant.now().minusSeconds(300));
        fcmTokens.put(token1.getId(), token1);

        // FCM token for Team Windkracht Tien's device
        FcmToken token2 = new FcmToken();
        token2.setId(FCM_TOKEN_WINDKRACHT_ID);
        token2.setDeviceRegistrationId(MockDeviceRegistrationRepository.DEVICE_WINDKRACHT_ID);
        token2.setToken("mock-fcm-token-windkracht-xyz789");
        token2.setCreatedAt(Instant.now().minusSeconds(7200));
        token2.setUpdatedAt(Instant.now().minusSeconds(600));
        fcmTokens.put(token2.getId(), token2);
    }

    @Override
    public Optional<FcmToken> findById(UUID id) {
        return Optional.ofNullable(fcmTokens.get(id));
    }

    @Override
    public List<FcmToken> findAll() {
        return new ArrayList<>(fcmTokens.values());
    }

    @Override
    public Optional<FcmToken> findByDeviceRegistrationId(UUID deviceRegistrationId) {
        return fcmTokens.values().stream()
                .filter(t -> t.getDeviceRegistrationId().equals(deviceRegistrationId))
                .findFirst();
    }

    @Override
    public List<FcmToken> findByTeamId(UUID teamId) {
        // First find the device registration for this team
        Optional<DeviceRegistration> deviceReg = deviceRegistrationRepository.findByTeamId(teamId);
        if (deviceReg.isEmpty()) {
            return new ArrayList<>();
        }
        
        // Then find FCM tokens for that device
        return fcmTokens.values().stream()
                .filter(t -> t.getDeviceRegistrationId().equals(deviceReg.get().getId()))
                .collect(Collectors.toList());
    }

    @Override
    public FcmToken save(FcmToken fcmToken) {
        if (fcmToken.getId() == null) {
            fcmToken.setId(UUID.randomUUID());
        }
        fcmTokens.put(fcmToken.getId(), fcmToken);
        return fcmToken;
    }

    @Override
    public void delete(UUID id) {
        fcmTokens.remove(id);
    }

    @Override
    public void deleteByDeviceRegistrationId(UUID deviceRegistrationId) {
        fcmTokens.entrySet().removeIf(entry -> 
                entry.getValue().getDeviceRegistrationId().equals(deviceRegistrationId));
    }
}
