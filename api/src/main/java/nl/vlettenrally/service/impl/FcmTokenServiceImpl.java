package nl.vlettenrally.service.impl;

import io.quarkus.logging.Log;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import nl.vlettenrally.data.domain.DeviceRegistration;
import nl.vlettenrally.data.domain.FcmToken;
import nl.vlettenrally.data.repository.DeviceRegistrationRepository;
import nl.vlettenrally.data.repository.FcmTokenRepository;
import nl.vlettenrally.service.FcmTokenService;

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
    public void registerToken(UUID userId, boolean isAdmin, String token) {
        // Find or create device registration for this user
        DeviceRegistration deviceRegistration = findOrCreateDeviceRegistration(userId, isAdmin);

        // Update last seen
        deviceRegistration.touch();
        deviceRegistrationRepository.save(deviceRegistration);

        String userType = isAdmin ? "admin" : "team";

        // First check if this exact token already exists in the system
        Optional<FcmToken> existingTokenByValue = fcmTokenRepository.findByToken(token);

        if (existingTokenByValue.isPresent()) {
            // Token already exists - update it to point to current device registration
            FcmToken fcmToken = existingTokenByValue.get();
            if (!fcmToken.getDeviceRegistrationId().equals(deviceRegistration.getId())) {
                // Token was registered to a different device, reassign it
                fcmToken.setDeviceRegistrationId(deviceRegistration.getId());
                fcmTokenRepository.save(fcmToken);
                Log.infof("Reassigned existing FCM token to %s %s", userType, userId);
            } else {
                // Token already registered to this device, just update timestamp
                fcmTokenRepository.save(fcmToken);
                Log.infof("FCM token already registered for %s %s", userType, userId);
            }
        } else {
            // Token doesn't exist - check if device already has a token
            Optional<FcmToken> existingTokenForDevice = fcmTokenRepository.findByDeviceRegistrationId(deviceRegistration.getId());

            if (existingTokenForDevice.isPresent()) {
                // Device has an old token, replace it with the new one
                FcmToken fcmToken = existingTokenForDevice.get();
                fcmToken.setToken(token);
                fcmTokenRepository.save(fcmToken);
                Log.infof("Updated FCM token for %s %s", userType, userId);
            } else {
                // Create new token for this device
                FcmToken fcmToken = new FcmToken();
                fcmToken.setDeviceRegistrationId(deviceRegistration.getId());
                fcmToken.setToken(token);
                fcmTokenRepository.save(fcmToken);
                Log.infof("Registered new FCM token for %s %s", userType, userId);
            }
        }
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
     * Find existing device registration or create a new one.
     */
    private DeviceRegistration findOrCreateDeviceRegistration(UUID userId, boolean isAdmin) {
        if (isAdmin) {
            return deviceRegistrationRepository.findByAdminId(userId)
                    .orElseGet(() -> {
                        DeviceRegistration newRegistration = new DeviceRegistration();
                        newRegistration.setAdminId(userId);
                        return deviceRegistrationRepository.save(newRegistration);
                    });
        } else {
            return deviceRegistrationRepository.findByTeamId(userId)
                    .orElseGet(() -> {
                        DeviceRegistration newRegistration = new DeviceRegistration();
                        newRegistration.setTeamId(userId);
                        return deviceRegistrationRepository.save(newRegistration);
                    });
        }
    }
}

