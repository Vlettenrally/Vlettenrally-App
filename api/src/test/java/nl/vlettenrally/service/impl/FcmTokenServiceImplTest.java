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
        when(fcmTokenRepository.findByDeviceRegistrationId(deviceRegistrationId)).thenReturn(Optional.empty());
        when(fcmTokenRepository.save(any(FcmToken.class))).thenAnswer(invocation -> invocation.getArgument(0));

        // Act
        fcmTokenService.registerToken(teamId, false, fcmToken, deviceRegistrationId, null);

        // Assert
        ArgumentCaptor<FcmToken> tokenCaptor = ArgumentCaptor.forClass(FcmToken.class);
        verify(fcmTokenRepository).save(tokenCaptor.capture());
        assertEquals(deviceRegistrationId, tokenCaptor.getValue().getDeviceRegistrationId());
        assertEquals(fcmToken, tokenCaptor.getValue().getToken());
        // Target device had no token, so nothing to clean up.
        verify(fcmTokenRepository, never()).delete(any());
    }

    @Test
    void registerToken_reassign_deletesStaleTokenOnTargetDevice() {
        // Arrange — the incoming stable token belongs to a different device (the
        // previous team/admin on this physical device), while the target device
        // already holds a different, stale token.
        UUID otherDeviceId = UUID.randomUUID();
        DeviceRegistration targetDevice = createDeviceRegistration(teamId, null);
        FcmToken tokenOnOtherDevice = createFcmToken(fcmToken, otherDeviceId);
        FcmToken staleTokenOnTarget = createFcmToken("stale-token", deviceRegistrationId);

        when(deviceRegistrationRepository.findByTeamIdAndId(teamId, deviceRegistrationId)).thenReturn(Optional.of(targetDevice));
        when(deviceRegistrationRepository.save(any(DeviceRegistration.class))).thenReturn(targetDevice);
        when(fcmTokenRepository.findByToken(fcmToken)).thenReturn(Optional.of(tokenOnOtherDevice));
        when(fcmTokenRepository.findByDeviceRegistrationId(deviceRegistrationId)).thenReturn(Optional.of(staleTokenOnTarget));
        when(fcmTokenRepository.save(any(FcmToken.class))).thenAnswer(invocation -> invocation.getArgument(0));

        // Act
        fcmTokenService.registerToken(teamId, false, fcmToken, deviceRegistrationId, null);

        // Assert — stale token freed, incoming token reassigned to the target device.
        verify(fcmTokenRepository).delete(staleTokenOnTarget.getId());
        ArgumentCaptor<FcmToken> tokenCaptor = ArgumentCaptor.forClass(FcmToken.class);
        verify(fcmTokenRepository).save(tokenCaptor.capture());
        assertEquals(tokenOnOtherDevice.getId(), tokenCaptor.getValue().getId());
        assertEquals(deviceRegistrationId, tokenCaptor.getValue().getDeviceRegistrationId());
    }

    @Test
    void registerToken_constraintViolationOnInsert_recoversViaReassign() {
        // Arrange — a concurrent registration of the same token wins the race: our
        // first INSERT trips the unique constraint, after which the competing row is
        // visible and we recover by reassigning it to our (same) device.
        DeviceRegistration targetDevice = createDeviceRegistration(teamId, null);
        FcmToken racedToken = createFcmToken(fcmToken, deviceRegistrationId);

        when(deviceRegistrationRepository.findByTeamIdAndId(teamId, deviceRegistrationId)).thenReturn(Optional.of(targetDevice));
        when(deviceRegistrationRepository.save(any(DeviceRegistration.class))).thenReturn(targetDevice);
        // First pass: token absent, device empty -> attempt insert. Second pass: the
        // competitor's row is now visible.
        when(fcmTokenRepository.findByToken(fcmToken))
                .thenReturn(Optional.empty())
                .thenReturn(Optional.of(racedToken));
        when(fcmTokenRepository.findByDeviceRegistrationId(deviceRegistrationId)).thenReturn(Optional.empty());
        when(fcmTokenRepository.save(any(FcmToken.class)))
                .thenThrow(new org.hibernate.exception.ConstraintViolationException(
                        "duplicate key", new java.sql.SQLException("dup", "23505"), "uq_fcm_tokens_token"))
                .thenAnswer(invocation -> invocation.getArgument(0));

        // Act — must not throw.
        UUID result = fcmTokenService.registerToken(teamId, false, fcmToken, deviceRegistrationId, null);

        // Assert — recovered: save attempted twice (failed insert, then touch).
        assertEquals(deviceRegistrationId, result);
        verify(fcmTokenRepository, times(2)).save(any(FcmToken.class));
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

