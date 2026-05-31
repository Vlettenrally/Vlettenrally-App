package nl.vlettenrally.service.impl;

import nl.vlettenrally.data.domain.DeviceRegistration;
import nl.vlettenrally.data.domain.FcmToken;
import nl.vlettenrally.data.repository.DeviceRegistrationRepository;
import nl.vlettenrally.data.repository.FcmTokenRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Unit tests for FcmTokenServiceImpl.
 * Tests token registration, reassignment, and unregistration.
 */
@ExtendWith(MockitoExtension.class)
class FcmTokenServiceImplTest {

    @Mock
    FcmTokenRepository fcmTokenRepository;

    @Mock
    DeviceRegistrationRepository deviceRegistrationRepository;

    @InjectMocks
    FcmTokenServiceImpl fcmTokenService;

    private UUID teamId;
    private UUID adminId;
    private UUID deviceRegistrationId;
    private String fcmToken;

    @BeforeEach
    void setUp() {
        teamId = UUID.randomUUID();
        adminId = UUID.randomUUID();
        deviceRegistrationId = UUID.randomUUID();
        fcmToken = "test-fcm-token-12345";
    }

    // ==================== Register Token Tests ====================

    @Test
    void registerToken_newTeamToken_createsDeviceRegistrationAndToken() {
        // Arrange — no hint, no existing row: service goes straight to save() with a fresh row.
        when(deviceRegistrationRepository.save(any(DeviceRegistration.class)))
                .thenAnswer(invocation -> {
                    DeviceRegistration dr = invocation.getArgument(0);
                    dr.setId(deviceRegistrationId);
                    return dr;
                });
        when(fcmTokenRepository.findByToken(fcmToken)).thenReturn(Optional.empty());
        when(fcmTokenRepository.findByDeviceRegistrationId(deviceRegistrationId)).thenReturn(Optional.empty());
        when(fcmTokenRepository.save(any(FcmToken.class))).thenAnswer(invocation -> invocation.getArgument(0));

        // Act
        fcmTokenService.registerToken(teamId, false, fcmToken, null, null);

        // Assert
        ArgumentCaptor<DeviceRegistration> drCaptor = ArgumentCaptor.forClass(DeviceRegistration.class);
        verify(deviceRegistrationRepository, times(2)).save(drCaptor.capture());
        assertEquals(teamId, drCaptor.getAllValues().getFirst().getTeamId());

        ArgumentCaptor<FcmToken> tokenCaptor = ArgumentCaptor.forClass(FcmToken.class);
        verify(fcmTokenRepository).save(tokenCaptor.capture());
        assertEquals(fcmToken, tokenCaptor.getValue().getToken());
        assertEquals(deviceRegistrationId, tokenCaptor.getValue().getDeviceRegistrationId());
    }

    @Test
    void registerToken_newAdminToken_createsDeviceRegistrationAndToken() {
        // Arrange — no hint, no existing row: service goes straight to save() with a fresh row.
        when(deviceRegistrationRepository.save(any(DeviceRegistration.class)))
                .thenAnswer(invocation -> {
                    DeviceRegistration dr = invocation.getArgument(0);
                    dr.setId(deviceRegistrationId);
                    return dr;
                });
        when(fcmTokenRepository.findByToken(fcmToken)).thenReturn(Optional.empty());
        when(fcmTokenRepository.findByDeviceRegistrationId(deviceRegistrationId)).thenReturn(Optional.empty());
        when(fcmTokenRepository.save(any(FcmToken.class))).thenAnswer(invocation -> invocation.getArgument(0));

        // Act
        fcmTokenService.registerToken(adminId, true, fcmToken, null, null);

        // Assert
        ArgumentCaptor<DeviceRegistration> drCaptor = ArgumentCaptor.forClass(DeviceRegistration.class);
        verify(deviceRegistrationRepository, times(2)).save(drCaptor.capture());
        assertEquals(adminId, drCaptor.getAllValues().getFirst().getAdminId());

        ArgumentCaptor<FcmToken> tokenCaptor = ArgumentCaptor.forClass(FcmToken.class);
        verify(fcmTokenRepository).save(tokenCaptor.capture());
        assertEquals(fcmToken, tokenCaptor.getValue().getToken());
    }

    @Test
    void registerToken_existingDeviceWithDifferentToken_replacesToken() {
        // Arrange
        DeviceRegistration existingDevice = createDeviceRegistration(teamId, null);
        FcmToken existingFcmToken = createFcmToken("old-token", deviceRegistrationId);

        when(deviceRegistrationRepository.findByTeamIdAndId(teamId, deviceRegistrationId)).thenReturn(Optional.of(existingDevice));
        when(deviceRegistrationRepository.save(any(DeviceRegistration.class))).thenReturn(existingDevice);
        when(fcmTokenRepository.findByToken(fcmToken)).thenReturn(Optional.empty());
        when(fcmTokenRepository.findByDeviceRegistrationId(deviceRegistrationId)).thenReturn(Optional.of(existingFcmToken));
        when(fcmTokenRepository.save(any(FcmToken.class))).thenAnswer(invocation -> invocation.getArgument(0));

        // Act
        fcmTokenService.registerToken(teamId, false, fcmToken, deviceRegistrationId, null);

        // Assert
        ArgumentCaptor<FcmToken> tokenCaptor = ArgumentCaptor.forClass(FcmToken.class);
        verify(fcmTokenRepository).save(tokenCaptor.capture());
        assertEquals(fcmToken, tokenCaptor.getValue().getToken());
        assertEquals(existingFcmToken.getId(), tokenCaptor.getValue().getId());
    }

    @Test
    void registerToken_tokenExistsForDifferentDevice_reassignsToken() {
        // Arrange
        UUID otherDeviceId = UUID.randomUUID();
        DeviceRegistration existingDevice = createDeviceRegistration(teamId, null);
        FcmToken existingFcmToken = createFcmToken(fcmToken, otherDeviceId);

        when(deviceRegistrationRepository.findByTeamIdAndId(teamId, deviceRegistrationId)).thenReturn(Optional.of(existingDevice));
        when(deviceRegistrationRepository.save(any(DeviceRegistration.class))).thenReturn(existingDevice);
        when(fcmTokenRepository.findByToken(fcmToken)).thenReturn(Optional.of(existingFcmToken));
        when(fcmTokenRepository.save(any(FcmToken.class))).thenAnswer(invocation -> invocation.getArgument(0));

        // Act
        fcmTokenService.registerToken(teamId, false, fcmToken, deviceRegistrationId, null);

        // Assert
        ArgumentCaptor<FcmToken> tokenCaptor = ArgumentCaptor.forClass(FcmToken.class);
        verify(fcmTokenRepository).save(tokenCaptor.capture());
        assertEquals(deviceRegistrationId, tokenCaptor.getValue().getDeviceRegistrationId());
        assertEquals(fcmToken, tokenCaptor.getValue().getToken());
    }

    @Test
    void registerToken_tokenAlreadyRegisteredToSameDevice_updatesTimestamp() {
        // Arrange
        DeviceRegistration existingDevice = createDeviceRegistration(teamId, null);
        FcmToken existingFcmToken = createFcmToken(fcmToken, deviceRegistrationId);

        when(deviceRegistrationRepository.findByTeamIdAndId(teamId, deviceRegistrationId)).thenReturn(Optional.of(existingDevice));
        when(deviceRegistrationRepository.save(any(DeviceRegistration.class))).thenReturn(existingDevice);
        when(fcmTokenRepository.findByToken(fcmToken)).thenReturn(Optional.of(existingFcmToken));
        when(fcmTokenRepository.save(any(FcmToken.class))).thenAnswer(invocation -> invocation.getArgument(0));

        // Act
        fcmTokenService.registerToken(teamId, false, fcmToken, deviceRegistrationId, null);

        // Assert
        verify(fcmTokenRepository).save(existingFcmToken);
        // Token should not have its deviceRegistrationId changed
        assertEquals(deviceRegistrationId, existingFcmToken.getDeviceRegistrationId());
    }

    @Test
    void registerToken_existingDeviceRegistration_updatesLastSeen() {
        // Arrange
        DeviceRegistration existingDevice = createDeviceRegistration(teamId, null);

        when(deviceRegistrationRepository.findByTeamIdAndId(teamId, deviceRegistrationId)).thenReturn(Optional.of(existingDevice));
        when(deviceRegistrationRepository.save(any(DeviceRegistration.class))).thenReturn(existingDevice);
        when(fcmTokenRepository.findByToken(fcmToken)).thenReturn(Optional.empty());
        when(fcmTokenRepository.findByDeviceRegistrationId(deviceRegistrationId)).thenReturn(Optional.empty());
        when(fcmTokenRepository.save(any(FcmToken.class))).thenAnswer(invocation -> invocation.getArgument(0));

        // Act
        fcmTokenService.registerToken(teamId, false, fcmToken, deviceRegistrationId, null);

        // Assert
        verify(deviceRegistrationRepository).save(existingDevice);
    }

    // ==================== Unregister Token Tests ====================

    @Test
    void unregisterToken_existingTeamDevice_deletesToken() {
        // Arrange
        DeviceRegistration existingDevice = createDeviceRegistration(teamId, null);
        when(deviceRegistrationRepository.findByTeamId(teamId)).thenReturn(Optional.of(existingDevice));

        // Act
        fcmTokenService.unregisterToken(teamId, false);

        // Assert
        verify(fcmTokenRepository).deleteByDeviceRegistrationId(deviceRegistrationId);
    }

    @Test
    void unregisterToken_existingAdminDevice_deletesToken() {
        // Arrange
        DeviceRegistration existingDevice = createDeviceRegistration(null, adminId);
        when(deviceRegistrationRepository.findByAdminId(adminId)).thenReturn(Optional.of(existingDevice));

        // Act
        fcmTokenService.unregisterToken(adminId, true);

        // Assert
        verify(fcmTokenRepository).deleteByDeviceRegistrationId(deviceRegistrationId);
    }

    @Test
    void unregisterToken_noDeviceRegistration_doesNothing() {
        // Arrange
        when(deviceRegistrationRepository.findByTeamId(teamId)).thenReturn(Optional.empty());

        // Act
        fcmTokenService.unregisterToken(teamId, false);

        // Assert
        verify(fcmTokenRepository, never()).deleteByDeviceRegistrationId(any());
    }

    // ==================== Helper Methods ====================

    private DeviceRegistration createDeviceRegistration(UUID teamId, UUID adminId) {
        DeviceRegistration dr = new DeviceRegistration();
        dr.setId(deviceRegistrationId);
        dr.setTeamId(teamId);
        dr.setAdminId(adminId);
        return dr;
    }

    private FcmToken createFcmToken(String token, UUID deviceRegId) {
        FcmToken fcmToken = new FcmToken();
        fcmToken.setId(UUID.randomUUID());
        fcmToken.setToken(token);
        fcmToken.setDeviceRegistrationId(deviceRegId);
        return fcmToken;
    }
}

