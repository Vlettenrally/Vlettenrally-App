package nl.vlettenrally.data.repository;

import nl.vlettenrally.data.domain.FcmToken;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Repository interface for FcmToken entities
 */
public interface FcmTokenRepository {
    
    Optional<FcmToken> findById(UUID id);
    
    List<FcmToken> findAll();
    
    Optional<FcmToken> findByDeviceRegistrationId(UUID deviceRegistrationId);
    
    /**
     * Find all FCM tokens for a team (via their device registrations)
     */
    List<FcmToken> findByTeamId(UUID teamId);
    
    /**
     * Find an FCM token by its token string value
     */
    Optional<FcmToken> findByToken(String token);

    FcmToken save(FcmToken fcmToken);
    
    void delete(UUID id);
    
    void deleteByDeviceRegistrationId(UUID deviceRegistrationId);
}
