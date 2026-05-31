package nl.vlettenrally.data.repository;

import nl.vlettenrally.data.domain.DeviceRegistration;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Repository interface for DeviceRegistration entities
 */
public interface DeviceRegistrationRepository {
    
    Optional<DeviceRegistration> findById(UUID id);

    List<DeviceRegistration> findAll();

    Optional<DeviceRegistration> findByTeamId(UUID teamId);

    /**
     * Returns every device registered for a team, ordered by createdAt ASC (oldest first).
     * Used by the multi-device check-in coordination flow.
     */
    List<DeviceRegistration> findAllByTeamId(UUID teamId);

    /**
     * Look up a single device for a team by its id. Idempotency check for register-device.
     */
    Optional<DeviceRegistration> findByTeamIdAndId(UUID teamId, UUID deviceId);

    /**
     * Delete every device registered to a team. Used when an admin resets check-in.
     */
    void deleteByTeamId(UUID teamId);

    Optional<DeviceRegistration> findByAdminId(UUID adminId);

    /**
     * Returns every device registered for an admin, ordered by createdAt ASC.
     * Mirrors {@link #findAllByTeamId} for the admin-side multi-device flow.
     */
    List<DeviceRegistration> findAllByAdminId(UUID adminId);

    /**
     * Look up a single device for an admin by its id. Idempotency check for admin
     * FCM registration and target for self-service rename/remove endpoints.
     */
    Optional<DeviceRegistration> findByAdminIdAndId(UUID adminId, UUID deviceId);

    DeviceRegistration save(DeviceRegistration deviceRegistration);

    void updateLastSeen(UUID id);

    void delete(UUID id);
}
