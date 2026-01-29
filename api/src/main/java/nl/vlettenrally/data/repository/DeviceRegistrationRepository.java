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
    
    DeviceRegistration save(DeviceRegistration deviceRegistration);
    
    void updateLastSeen(UUID id);
    
    void delete(UUID id);
}
