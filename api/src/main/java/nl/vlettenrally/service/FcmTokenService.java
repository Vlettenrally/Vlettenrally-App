package nl.vlettenrally.service;

import java.util.UUID;

/**
 * Service interface for FCM Token operations.
 * Handles registration and management of Firebase Cloud Messaging tokens.
 */
public interface FcmTokenService {

    /**
     * Register or update an FCM token for a user (team or admin).
     *
     * This method ensures:
     * - If the token already exists, it's reassigned to the current device
     * - If the device already has a different token, it's replaced
     * - Otherwise, a new token is created
     *
     * @param userId The ID of the user (team or admin)
     * @param isAdmin Whether the user is an admin
     * @param token The FCM token to register
     */
    UUID registerToken(UUID userId, boolean isAdmin, String token, UUID deviceIdHint, String nickname);

    /**
     * Unregister an FCM token for a user.
     * Removes the FCM token associated with the user's device registration.
     *
     * @param userId The ID of the user (team or admin)
     * @param isAdmin Whether the user is an admin
     */
    void unregisterToken(UUID userId, boolean isAdmin);
}

