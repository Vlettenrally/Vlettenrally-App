package nl.vlettenrally.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import nl.vlettenrally.data.domain.DeviceRegistration;
import nl.vlettenrally.data.domain.FcmToken;
import nl.vlettenrally.data.repository.DeviceRegistrationRepository;
import nl.vlettenrally.data.repository.FcmTokenRepository;
import nl.vlettenrally.service.impl.FcmTokenServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.UUID;

/**
 * Unit tests for FcmTokenService implementation.
 * Tests device registration and FCM token management logic.
 */
@ExtendWith(MockitoExtension.class)
class FcmTokenServiceTest {

    @Mock
    FcmTokenRepository fcmTokenRepository;

    @Mock
    DeviceRegistrationRepository deviceRegistrationRepository;

    @InjectMocks
    FcmTokenServiceImpl fcmTokenService;

    private UUID userId;
    private UUID deviceId;
    private String fcmToken;

    @BeforeEach
    void setUp() {
        userId = UUID.randomUUID();
        deviceId = UUID.randomUUID();
        fcmToken = "token_abc123";
    }

    // ==================== registerToken - Team User ====================

    @Test
    void registerToken_teamUserNewDevice_createsDeviceAndToken() {
        // Arrange — no hint, no existing row: service creates a fresh row directly.
        DeviceRegistration newDevice = createDeviceRegistration(userId, false);
        newDevice.setId(deviceId);
        when(deviceRegistrationRepository.save(any(DeviceRegistration.class))).thenReturn(newDevice);
        when(fcmTokenRepository.findByToken(fcmToken)).thenReturn(Optional.empty());
        when(fcmTokenRepository.findByDeviceRegistrationId(deviceId)).thenReturn(Optional.empty());
        when(fcmTokenRepository.save(any(FcmToken.class))).thenAnswer(invocation -> invocation.getArgument(0));

        // Act
        fcmTokenService.registerToken(userId, false, fcmToken, null, null);

        // Assert
        verify(deviceRegistrationRepository, times(2)).save(any(DeviceRegistration.class));
        verify(fcmTokenRepository).findByToken(fcmToken);
        verify(fcmTokenRepository).findByDeviceRegistrationId(deviceId);
        verify(fcmTokenRepository).save(any(FcmToken.class));
    }

    @Test
    void registerToken_teamUserExistingDevice_updatesToken() {
        // Arrange
        DeviceRegistration existingDevice = createDeviceRegistration(userId, false);
        existingDevice.setId(deviceId);
        when(deviceRegistrationRepository.findByTeamIdAndId(userId, deviceId)).thenReturn(Optional.of(existingDevice));
        when(deviceRegistrationRepository.save(any(DeviceRegistration.class))).thenReturn(existingDevice);
        when(fcmTokenRepository.findByToken(fcmToken)).thenReturn(Optional.empty());

        FcmToken oldToken = new FcmToken();
        oldToken.setDeviceRegistrationId(deviceId);
        oldToken.setToken("old_token");
        when(fcmTokenRepository.findByDeviceRegistrationId(deviceId)).thenReturn(Optional.of(oldToken));
        when(fcmTokenRepository.save(any(FcmToken.class))).thenAnswer(invocation -> invocation.getArgument(0));

        // Act
        fcmTokenService.registerToken(userId, false, fcmToken, deviceId, null);

        // Assert
        verify(fcmTokenRepository).findByDeviceRegistrationId(deviceId);
        verify(fcmTokenRepository).save(oldToken);
        assertEquals(fcmToken, oldToken.getToken());
    }

    @Test
    void registerToken_teamUserTokenAlreadyExists_reassignsToCurrentDevice() {
        // Arrange
        DeviceRegistration device = createDeviceRegistration(userId, false);
        device.setId(deviceId);
        when(deviceRegistrationRepository.findByTeamIdAndId(userId, deviceId)).thenReturn(Optional.of(device));
        when(deviceRegistrationRepository.save(any(DeviceRegistration.class))).thenReturn(device);

        UUID oldDeviceId = UUID.randomUUID();
        FcmToken existingToken = new FcmToken();
        existingToken.setToken(fcmToken);
        existingToken.setDeviceRegistrationId(oldDeviceId);
        when(fcmTokenRepository.findByToken(fcmToken)).thenReturn(Optional.of(existingToken));
        when(fcmTokenRepository.save(any(FcmToken.class))).thenAnswer(invocation -> invocation.getArgument(0));

        // Act
        fcmTokenService.registerToken(userId, false, fcmToken, deviceId, null);

        // Assert
        verify(fcmTokenRepository).findByToken(fcmToken);
        verify(fcmTokenRepository).save(existingToken);
        assertEquals(deviceId, existingToken.getDeviceRegistrationId());
    }

    // ==================== registerToken - Admin User ====================

    @Test
    void registerToken_adminUserNewDevice_createsDeviceAndToken() {
        // Arrange — no hint, no existing row: service creates a fresh row directly.
        DeviceRegistration newDevice = createDeviceRegistration(userId, true);
        newDevice.setId(deviceId);
        when(deviceRegistrationRepository.save(any(DeviceRegistration.class))).thenReturn(newDevice);
        when(fcmTokenRepository.findByToken(fcmToken)).thenReturn(Optional.empty());
        when(fcmTokenRepository.findByDeviceRegistrationId(deviceId)).thenReturn(Optional.empty());
        when(fcmTokenRepository.save(any(FcmToken.class))).thenAnswer(invocation -> invocation.getArgument(0));

        // Act
        fcmTokenService.registerToken(userId, true, fcmToken, null, null);

        // Assert
        verify(deviceRegistrationRepository, times(2)).save(any(DeviceRegistration.class));
        verify(fcmTokenRepository).save(any(FcmToken.class));
    }

    @Test
    void registerToken_adminUserExistingDevice_updatesToken() {
        // Arrange
        DeviceRegistration existingDevice = createDeviceRegistration(userId, true);
        existingDevice.setId(deviceId);
        when(deviceRegistrationRepository.findByAdminIdAndId(userId, deviceId)).thenReturn(Optional.of(existingDevice));
        when(deviceRegistrationRepository.save(any(DeviceRegistration.class))).thenReturn(existingDevice);
        when(fcmTokenRepository.findByToken(fcmToken)).thenReturn(Optional.empty());

        FcmToken oldToken = new FcmToken();
        oldToken.setDeviceRegistrationId(deviceId);
        oldToken.setToken("old_token");
        when(fcmTokenRepository.findByDeviceRegistrationId(deviceId)).thenReturn(Optional.of(oldToken));
        when(fcmTokenRepository.save(any(FcmToken.class))).thenAnswer(invocation -> invocation.getArgument(0));

        // Act
        fcmTokenService.registerToken(userId, true, fcmToken, deviceId, null);

        // Assert
        verify(fcmTokenRepository).save(oldToken);
    }

    // ==================== registerToken - Token Already Exists ====================

    @Test
    void registerToken_tokenAlreadyRegisteredToSameDevice_justUpdatesTimestamp() {
        // Arrange
        DeviceRegistration device = createDeviceRegistration(userId, false);
        device.setId(deviceId);
        when(deviceRegistrationRepository.findByTeamIdAndId(userId, deviceId)).thenReturn(Optional.of(device));
        when(deviceRegistrationRepository.save(any(DeviceRegistration.class))).thenReturn(device);

        FcmToken existingToken = new FcmToken();
        existingToken.setToken(fcmToken);
        existingToken.setDeviceRegistrationId(deviceId);
        when(fcmTokenRepository.findByToken(fcmToken)).thenReturn(Optional.of(existingToken));
        when(fcmTokenRepository.save(any(FcmToken.class))).thenAnswer(invocation -> invocation.getArgument(0));

        // Act
        fcmTokenService.registerToken(userId, false, fcmToken, deviceId, null);

        // Assert
        verify(fcmTokenRepository).findByToken(fcmToken);
        verify(fcmTokenRepository).save(existingToken);
        assertEquals(deviceId, existingToken.getDeviceRegistrationId());
    }

    // ==================== unregisterToken - Team User ====================

    @Test
    void unregisterToken_teamUserWithDevice_deletesFcmToken() {
        // Arrange
        DeviceRegistration device = createDeviceRegistration(userId, false);
        device.setId(deviceId);
        when(deviceRegistrationRepository.findByTeamId(userId)).thenReturn(Optional.of(device));
        doNothing().when(fcmTokenRepository).deleteByDeviceRegistrationId(deviceId);

        // Act
        fcmTokenService.unregisterToken(userId, false);

        // Assert
        verify(deviceRegistrationRepository).findByTeamId(userId);
        verify(fcmTokenRepository).deleteByDeviceRegistrationId(deviceId);
    }

    @Test
    void unregisterToken_teamUserNoDevice_doesNothing() {
        // Arrange
        when(deviceRegistrationRepository.findByTeamId(userId)).thenReturn(Optional.empty());

        // Act
        fcmTokenService.unregisterToken(userId, false);

        // Assert
        verify(deviceRegistrationRepository).findByTeamId(userId);
        verify(fcmTokenRepository, never()).deleteByDeviceRegistrationId(any());
    }

    // ==================== unregisterToken - Admin User ====================

    @Test
    void unregisterToken_adminUserWithDevice_deletesFcmToken() {
        // Arrange
        DeviceRegistration device = createDeviceRegistration(userId, true);
        device.setId(deviceId);
        when(deviceRegistrationRepository.findByAdminId(userId)).thenReturn(Optional.of(device));
        doNothing().when(fcmTokenRepository).deleteByDeviceRegistrationId(deviceId);

        // Act
        fcmTokenService.unregisterToken(userId, true);

        // Assert
        verify(deviceRegistrationRepository).findByAdminId(userId);
        verify(fcmTokenRepository).deleteByDeviceRegistrationId(deviceId);
    }

    @Test
    void unregisterToken_adminUserNoDevice_doesNothing() {
        // Arrange
        when(deviceRegistrationRepository.findByAdminId(userId)).thenReturn(Optional.empty());

        // Act
        fcmTokenService.unregisterToken(userId, true);

        // Assert
        verify(deviceRegistrationRepository).findByAdminId(userId);
        verify(fcmTokenRepository, never()).deleteByDeviceRegistrationId(any());
    }

    // ==================== Helper Methods ====================

    private DeviceRegistration createDeviceRegistration(UUID userId, boolean isAdmin) {
        DeviceRegistration device = new DeviceRegistration();
        device.setId(UUID.randomUUID());
        if (isAdmin) {
            device.setAdminId(userId);
        } else {
            device.setTeamId(userId);
        }
        return device;
    }
}


