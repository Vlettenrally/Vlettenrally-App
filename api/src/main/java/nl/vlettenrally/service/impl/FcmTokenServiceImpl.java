package nl.vlettenrally.service.impl;

import io.quarkus.logging.Log;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import nl.vlettenrally.data.domain.DeviceRegistration;
import nl.vlettenrally.data.domain.FcmToken;
import nl.vlettenrally.data.repository.DeviceRegistrationRepository;
import nl.vlettenrally.data.repository.FcmTokenRepository;
import nl.vlettenrally.service.FcmTokenService;
import nl.vlettenrally.service.NicknameSanitizer;

import java.sql.SQLException;
import java.util.Optional;
import java.util.UUID;

/**
 * Implementation of FcmTokenService.
 * Handles FCM token registration ensuring no duplicates.
 */
@ApplicationScoped
public class FcmTokenServiceImpl implements FcmTokenService {

    @Inject
    FcmTokenRepository fcmTokenRepository;

    @Inject
    DeviceRegistrationRepository deviceRegistrationRepository;

    @Override
    public UUID registerToken(UUID userId, boolean isAdmin, String token, UUID deviceIdHint, String nickname) {
        // Find or create device registration for this user. The hint is only honored
        // when it matches a row already owned by this user — otherwise the server
        // allocates a fresh UUID.
        DeviceRegistration deviceRegistration = findOrCreateDeviceRegistration(userId, isAdmin, deviceIdHint);
        // Apply a nickname only when the row doesn't already have one — admins can
        // rename via PATCH /admin/me/devices/{id} and we don't want repeated FCM
        // registrations to clobber that.
        String sanitizedNickname = NicknameSanitizer.sanitize(nickname);
        if (sanitizedNickname != null && !sanitizedNickname.isBlank()
                && (deviceRegistration.getNickname() == null || deviceRegistration.getNickname().isBlank())) {
            deviceRegistration.setNickname(sanitizedNickname);
        }

        // Update last seen
        deviceRegistration.touch();
        deviceRegistrationRepository.save(deviceRegistration);

        String userType = isAdmin ? "admin" : "team";

        // Resolve the token against the unique constraints (one row per token value,
        // one row per device). A concurrent registration of the same stable token —
        // common on iOS PWAs that re-register on every foreground — can lose the
        // check-then-act race and hit a unique violation on INSERT. In that case the
        // competing row now exists, so re-running takes the reassign branch (an
        // idempotent touch for the same device), recovering without a 500.
        try {
            resolveToken(token, deviceRegistration.getId(), userType, userId);
        } catch (RuntimeException e) {
            if (!isConstraintViolation(e)) {
                throw e;
            }
            Log.infof("FCM token registration raced for %s %s, retrying as reassign", userType, userId);
            resolveToken(token, deviceRegistration.getId(), userType, userId);
        }

        return deviceRegistration.getId();
    }

    /**
     * Point the given token value at {@code deviceRegistrationId}, honouring both
     * unique constraints on {@code fcm_tokens}. When the token already exists on a
     * different device we reassign that row, first deleting any stale token already
     * occupying the target device so the per-device unique constraint isn't violated.
     */
    private void resolveToken(String token, UUID deviceRegistrationId, String userType, UUID userId) {
        // First check if this exact token already exists in the system
        Optional<FcmToken> existingTokenByValue = fcmTokenRepository.findByToken(token);

        if (existingTokenByValue.isPresent()) {
            // Token already exists - update it to point to current device registration
            FcmToken fcmToken = existingTokenByValue.get();
            if (!fcmToken.getDeviceRegistrationId().equals(deviceRegistrationId)) {
                // Token was registered to a different device (e.g. the previous team/admin
                // on this same physical device). Free the target device's slot first so we
                // don't trip the one-token-per-device unique constraint, then reassign.
                Optional<FcmToken> staleTokenForDevice = fcmTokenRepository.findByDeviceRegistrationId(deviceRegistrationId);
                if (staleTokenForDevice.isPresent() && !staleTokenForDevice.get().getId().equals(fcmToken.getId())) {
                    fcmTokenRepository.delete(staleTokenForDevice.get().getId());
                }
                fcmToken.setDeviceRegistrationId(deviceRegistrationId);
                fcmTokenRepository.save(fcmToken);
                Log.infof("Reassigned existing FCM token to %s %s", userType, userId);
            } else {
                // Token already registered to this device, just update timestamp
                fcmTokenRepository.save(fcmToken);
                Log.infof("FCM token already registered for %s %s", userType, userId);
            }
        } else {
            // Token doesn't exist - check if device already has a token
            Optional<FcmToken> existingTokenForDevice = fcmTokenRepository.findByDeviceRegistrationId(deviceRegistrationId);

            if (existingTokenForDevice.isPresent()) {
                // Device has an old token, replace it with the new one
                FcmToken fcmToken = existingTokenForDevice.get();
                fcmToken.setToken(token);
                fcmTokenRepository.save(fcmToken);
                Log.infof("Updated FCM token for %s %s", userType, userId);
            } else {
                // Create new token for this device
                FcmToken fcmToken = new FcmToken();
                fcmToken.setDeviceRegistrationId(deviceRegistrationId);
                fcmToken.setToken(token);
                fcmTokenRepository.save(fcmToken);
                Log.infof("Registered new FCM token for %s %s", userType, userId);
            }
        }
    }

    /**
     * Detect a database unique-constraint violation anywhere in the cause chain.
     * Quarkus/Hibernate may wrap the underlying SQL exception, so we walk causes
     * looking for Hibernate's ConstraintViolationException or PostgreSQL's unique
     * violation SQLState (23505).
     */
    private boolean isConstraintViolation(Throwable t) {
        for (Throwable cause = t; cause != null; cause = cause.getCause()) {
            if (cause instanceof org.hibernate.exception.ConstraintViolationException) {
                return true;
            }
            if (cause instanceof SQLException sqlException && "23505".equals(sqlException.getSQLState())) {
                return true;
            }
            if (cause.getCause() == cause) {
                break;
            }
        }
        return false;
    }

    @Override
    public void unregisterToken(UUID userId, boolean isAdmin) {
        String userType = isAdmin ? "admin" : "team";

        // Find device registration for this user
        Optional<DeviceRegistration> deviceRegistration = isAdmin
                ? deviceRegistrationRepository.findByAdminId(userId)
                : deviceRegistrationRepository.findByTeamId(userId);

        if (deviceRegistration.isPresent()) {
            // Delete FCM token for this device
            fcmTokenRepository.deleteByDeviceRegistrationId(deviceRegistration.get().getId());
            Log.infof("Unregistered FCM token for %s %s", userType, userId);
        }
    }

    /**
     * Find existing device registration or create a new one. The client-supplied
     * hint is only used when it resolves to a row already owned by this user; an
     * unknown hint is discarded and a fresh server-side UUID is allocated, so the
     * client cannot dictate the row id.
     */
    private DeviceRegistration findOrCreateDeviceRegistration(UUID userId, boolean isAdmin, UUID deviceIdHint) {
        if (isAdmin) {
            if (deviceIdHint != null) {
                Optional<DeviceRegistration> hinted = deviceRegistrationRepository.findByAdminIdAndId(userId, deviceIdHint);
                if (hinted.isPresent()) {
                    return hinted.get();
                }
            }
            DeviceRegistration fresh = new DeviceRegistration();
            fresh.setAdminId(userId);
            return deviceRegistrationRepository.save(fresh);
        }
        if (deviceIdHint != null) {
            Optional<DeviceRegistration> hinted = deviceRegistrationRepository.findByTeamIdAndId(userId, deviceIdHint);
            if (hinted.isPresent()) {
                return hinted.get();
            }
        }
        DeviceRegistration fresh = new DeviceRegistration();
        fresh.setTeamId(userId);
        return deviceRegistrationRepository.save(fresh);
    }
}

