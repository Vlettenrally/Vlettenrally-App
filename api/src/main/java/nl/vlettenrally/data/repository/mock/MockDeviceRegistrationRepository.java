package nl.vlettenrally.data.repository.mock;

import io.quarkus.arc.profile.IfBuildProfile;
import jakarta.annotation.PostConstruct;
import jakarta.enterprise.context.ApplicationScoped;
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
 * Only active in dev and test profiles - replaced by PostgresDeviceRegistrationRepository in prod.
 */
@IfBuildProfile(anyOf = {"dev", "test"})
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
        return findAllByTeamId(teamId).stream().findFirst();
    }

    @Override
    public List<DeviceRegistration> findAllByTeamId(UUID teamId) {
        return deviceRegistrations.values().stream()
                .filter(d -> d.getTeamId() != null && d.getTeamId().equals(teamId))
                .sorted(java.util.Comparator.comparing(
                        DeviceRegistration::getCreatedAt,
                        java.util.Comparator.nullsLast(java.util.Comparator.naturalOrder())))
                .toList();
    }

    @Override
    public Optional<DeviceRegistration> findByTeamIdAndId(UUID teamId, UUID deviceId) {
        DeviceRegistration device = deviceRegistrations.get(deviceId);
        if (device == null || device.getTeamId() == null || !device.getTeamId().equals(teamId)) {
            return Optional.empty();
        }
        return Optional.of(device);
    }

    @Override
    public void deleteByTeamId(UUID teamId) {
        deviceRegistrations.values().removeIf(
                d -> d.getTeamId() != null && d.getTeamId().equals(teamId));
    }

    @Override
    public Optional<DeviceRegistration> findByAdminId(UUID adminId) {
        return findAllByAdminId(adminId).stream().findFirst();
    }

    @Override
    public List<DeviceRegistration> findAllByAdminId(UUID adminId) {
        return deviceRegistrations.values().stream()
                .filter(d -> d.getAdminId() != null && d.getAdminId().equals(adminId))
                .sorted(java.util.Comparator.comparing(
                        DeviceRegistration::getCreatedAt,
                        java.util.Comparator.nullsLast(java.util.Comparator.naturalOrder())))
                .toList();
    }

    @Override
    public Optional<DeviceRegistration> findByAdminIdAndId(UUID adminId, UUID deviceId) {
        DeviceRegistration device = deviceRegistrations.get(deviceId);
        if (device == null || device.getAdminId() == null || !device.getAdminId().equals(adminId)) {
            return Optional.empty();
        }
        return Optional.of(device);
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
