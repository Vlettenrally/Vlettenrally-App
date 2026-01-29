package nl.vlettenrally.data.repository.mock;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.Priority;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Alternative;
import nl.vlettenrally.data.domain.DeviceRegistration;
import nl.vlettenrally.data.repository.DeviceRegistrationRepository;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Mock implementation of DeviceRegistrationRepository with prefilled data.
 * Enabled by default in dev mode via @Priority.
 */
@Alternative
@Priority(1)
@ApplicationScoped
public class MockDeviceRegistrationRepository implements DeviceRegistrationRepository {

    public static final UUID DEVICE_WATERVLOOIEN_ID = UUID.fromString("dddd0001-0000-0000-0000-000000000001");
    public static final UUID DEVICE_WINDKRACHT_ID = UUID.fromString("dddd0002-0000-0000-0000-000000000002");

    private final Map<UUID, DeviceRegistration> deviceRegistrations = new ConcurrentHashMap<>();

    @PostConstruct
    void init() {
        initializeData();
    }

    public void initializeData() {
        deviceRegistrations.clear();

        // Device for Team Watervlooien - active
        DeviceRegistration device1 = new DeviceRegistration();
        device1.setId(DEVICE_WATERVLOOIEN_ID);
        device1.setTeamId(MockTeamRepository.TEAM_WATERVLOOIEN_ID);
        device1.setLastSeen(Instant.now());
        device1.setCreatedAt(Instant.now().minusSeconds(3600));
        deviceRegistrations.put(device1.getId(), device1);

        // Device for Team Windkracht Tien, last seen 10 minutes ago
        DeviceRegistration device2 = new DeviceRegistration();
        device2.setId(DEVICE_WINDKRACHT_ID);
        device2.setTeamId(MockTeamRepository.TEAM_WINDKRACHT_ID);
        device2.setLastSeen(Instant.now().minusSeconds(600)); // 10 minutes ago
        device2.setCreatedAt(Instant.now().minusSeconds(7200));
        deviceRegistrations.put(device2.getId(), device2);
    }

    @Override
    public Optional<DeviceRegistration> findById(UUID id) {
        return Optional.ofNullable(deviceRegistrations.get(id));
    }

    @Override
    public List<DeviceRegistration> findAll() {
        return new ArrayList<>(deviceRegistrations.values());
    }

    @Override
    public Optional<DeviceRegistration> findByTeamId(UUID teamId) {
        return deviceRegistrations.values().stream()
                .filter(d -> d.getTeamId().equals(teamId))
                .findFirst();
    }

    @Override
    public DeviceRegistration save(DeviceRegistration deviceRegistration) {
        if (deviceRegistration.getId() == null) {
            deviceRegistration.setId(UUID.randomUUID());
        }
        deviceRegistrations.put(deviceRegistration.getId(), deviceRegistration);
        return deviceRegistration;
    }

    @Override
    public void updateLastSeen(UUID id) {
        DeviceRegistration device = deviceRegistrations.get(id);
        if (device != null) {
            device.touch();
        }
    }

    @Override
    public void delete(UUID id) {
        deviceRegistrations.remove(id);
    }
}
