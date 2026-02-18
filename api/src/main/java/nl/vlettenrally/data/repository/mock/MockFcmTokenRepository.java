package nl.vlettenrally.data.repository.mock;

import io.quarkus.arc.profile.IfBuildProfile;
import jakarta.annotation.PostConstruct;
import jakarta.enterprise.context.ApplicationScoped;
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
 * Only active in dev and test profiles - replaced by PostgresFcmTokenRepository in prod.
 */
@IfBuildProfile(anyOf = {"dev", "test"})
@ApplicationScoped
public class MockFcmTokenRepository implements FcmTokenRepository {

    @Inject
    DeviceRegistrationRepository deviceRegistrationRepository;

    private final Map<UUID, FcmToken> fcmTokens = new ConcurrentHashMap<>();

    @PostConstruct
    void init() {
        initializeData();
    }

    public void initializeData() {
        fcmTokens.clear();
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
    public Optional<FcmToken> findByToken(String token) {
        return fcmTokens.values().stream()
                .filter(t -> t.getToken().equals(token))
                .findFirst();
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
