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
    /**
     * Register an FCM token. The client may pass a {@code deviceIdHint} (typically
     * the value previously returned and persisted in localStorage); the server treats
     * it as a resumption hint only — if no row exists for {@code (userId, hint)}, a
     * fresh server-side UUID is allocated. Returns the device id the token is now
     * attached to.
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

