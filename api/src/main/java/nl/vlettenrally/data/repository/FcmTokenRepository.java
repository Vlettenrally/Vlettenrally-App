package nl.vlettenrally.data.repository;

import nl.vlettenrally.data.domain.FcmToken;

import java.util.Collection;
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

    /**
     * Find all FCM tokens registered by admin users (for checkpoint/admin notifications).
     */
    List<FcmToken> findAllAdminTokens();

    /**
     * Find FCM tokens registered by the given admin user IDs.
     */
    List<FcmToken> findAdminTokensByAdminIds(Collection<UUID> adminIds);

    FcmToken save(FcmToken fcmToken);
    
    void delete(UUID id);
    
    void deleteByDeviceRegistrationId(UUID deviceRegistrationId);
}
