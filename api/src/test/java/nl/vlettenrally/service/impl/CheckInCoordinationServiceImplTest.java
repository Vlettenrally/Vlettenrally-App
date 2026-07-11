package nl.vlettenrally.service.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import nl.vlettenrally.data.domain.DeviceRegistration;
import nl.vlettenrally.data.domain.Game;
import nl.vlettenrally.data.domain.Team;
import nl.vlettenrally.data.dto.auth.RegisterDeviceResponse;
import nl.vlettenrally.data.dto.auth.TeamInvite;
import nl.vlettenrally.data.dto.auth.TokenResult;
import nl.vlettenrally.data.repository.DeviceRegistrationRepository;
import nl.vlettenrally.data.repository.GameRepository;
import nl.vlettenrally.data.repository.TeamRepository;
import nl.vlettenrally.service.AuthService;
import nl.vlettenrally.service.CheckInWindowClosedException;
import nl.vlettenrally.service.TeamNotCheckedInException;

/**
 * Unit tests for the check-in gates in registerDevice, plus the per-team
 * acceptingNewDevices computation. A device may only register for a team that has been
 * physically checked in ({@code isCheckedIn()}); once checked in, the digital check-in
 * window — measured from the physical check-in ({@code checkedInAt}) — applies to every
 * new device (first or additional). Resumed devices are never gated, and a valid invite
 * token (the team/admin QR path) bypasses the time window but not the physical-check-in
 * requirement.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class CheckInCoordinationServiceImplTest {

    @Mock GameRepository gameRepository;
    @Mock TeamRepository teamRepository;
    @Mock DeviceRegistrationRepository deviceRegistrationRepository;
    @Mock AuthService authService;

    CheckInCoordinationServiceImpl service;

    UUID gameId;
    UUID teamId;
    Game game;
    Team team;

    @BeforeEach
    void setUp() {
        service = new CheckInCoordinationServiceImpl();
        service.gameRepository = gameRepository;
        service.teamRepository = teamRepository;
        service.deviceRegistrationRepository = deviceRegistrationRepository;
        service.authService = authService;
        // Explicit value so the tests are independent of the configured default.
        service.digitalCheckinWindowMinutes = 15;
        service.maxDevicesPerTeam = 10;

        gameId = UUID.randomUUID();
        teamId = UUID.randomUUID();

        game = new Game();
        game.setId(gameId);

        team = new Team(teamId, gameId, "De Watervlooien", "boot 7");

        when(gameRepository.findById(gameId)).thenReturn(Optional.of(game));
        when(teamRepository.findById(teamId)).thenReturn(Optional.of(team));
        when(teamRepository.save(any(Team.class))).thenAnswer(inv -> inv.getArgument(0));
        when(deviceRegistrationRepository.save(any(DeviceRegistration.class)))
            .thenAnswer(inv -> inv.getArgument(0));
        when(authService.issueTeamTokens(any(Team.class), any()))
            .thenReturn(new TokenResult("access", "refresh", 3600L, teamId.toString(), team.getName(), "team"));
    }

    /** Mark the shared team physically checked in {@code minutesAgo} minutes ago. */
    private void physicallyCheckedIn(long minutesAgo) {
        team.setCheckedIn(true);
        team.setCheckedInAt(Instant.now().minus(minutesAgo, ChronoUnit.MINUTES));
    }

    private DeviceRegistration existingDevice() {
        return new DeviceRegistration(UUID.randomUUID(), teamId, gameId, "main");
    }

    @Test
    void registration_whenNotPhysicallyCheckedIn_isRejected() {
        // Team has never been checked in at the rally desk -> no digital check-in allowed.
        when(deviceRegistrationRepository.findAllByTeamId(teamId)).thenReturn(List.of());

        assertThrows(TeamNotCheckedInException.class,
            () -> service.registerDevice(gameId, teamId, null, "Lisa's phone", null));
    }

    @Test
    void registration_whenNotCheckedIn_isRejectedEvenWithInvite() {
        // A valid invite does not stand in for the team being physically present.
        when(deviceRegistrationRepository.findAllByTeamId(teamId)).thenReturn(List.of());
        when(authService.validateTeamInviteToken("good-token"))
            .thenReturn(Optional.of(new TeamInvite(gameId, teamId)));

        assertThrows(TeamNotCheckedInException.class,
            () -> service.registerDevice(gameId, teamId, null, "Phone via QR", "good-token"));
    }

    @Test
    void firstDevice_whenCheckedIn_withinWindow_isAllowed_andClaimsMain() {
        physicallyCheckedIn(5);
        when(deviceRegistrationRepository.findAllByTeamId(teamId)).thenReturn(List.of());

        RegisterDeviceResponse response =
            service.registerDevice(gameId, teamId, null, "Lisa's phone", null);

        assertTrue(response.isMainDevice());
        assertEquals(response.deviceId(), team.getMainDeviceId());
    }

    @Test
    void firstDevice_afterWindow_isRejected() {
        // Physically checked in 20 min ago, window is 15 min, no device registered yet:
        // even the first phone is now too late through the regular picker.
        physicallyCheckedIn(20);
        when(deviceRegistrationRepository.findAllByTeamId(teamId)).thenReturn(List.of());

        assertThrows(CheckInWindowClosedException.class,
            () -> service.registerDevice(gameId, teamId, null, "Late first phone", null));
    }

    @Test
    void device_withinWindow_isAllowed() {
        DeviceRegistration main = existingDevice();
        team.setMainDeviceId(main.getId());
        physicallyCheckedIn(5);
        when(deviceRegistrationRepository.findAllByTeamId(teamId)).thenReturn(List.of(main));
        when(deviceRegistrationRepository.findByTeamIdAndId(teamId, main.getId()))
            .thenReturn(Optional.of(main));

        RegisterDeviceResponse response =
            service.registerDevice(gameId, teamId, null, "Sanne's phone", null);

        assertFalse(response.isMainDevice());
    }

    @Test
    void device_afterWindow_isRejected() {
        DeviceRegistration main = existingDevice();
        team.setMainDeviceId(main.getId());
        physicallyCheckedIn(20);
        when(deviceRegistrationRepository.findAllByTeamId(teamId)).thenReturn(List.of(main));

        assertThrows(CheckInWindowClosedException.class,
            () -> service.registerDevice(gameId, teamId, null, "Late phone", null));
    }

    @Test
    void device_afterWindow_withValidInvite_isAllowed() {
        DeviceRegistration main = existingDevice();
        team.setMainDeviceId(main.getId());
        physicallyCheckedIn(20);
        when(deviceRegistrationRepository.findAllByTeamId(teamId)).thenReturn(List.of(main));
        when(deviceRegistrationRepository.findByTeamIdAndId(teamId, main.getId()))
            .thenReturn(Optional.of(main));
        when(authService.validateTeamInviteToken("good-token"))
            .thenReturn(Optional.of(new TeamInvite(gameId, teamId)));

        RegisterDeviceResponse response =
            service.registerDevice(gameId, teamId, null, "Late phone via QR", "good-token");

        assertFalse(response.isMainDevice());
    }

    @Test
    void device_afterWindow_withInviteForOtherTeam_isRejected() {
        DeviceRegistration main = existingDevice();
        team.setMainDeviceId(main.getId());
        physicallyCheckedIn(20);
        when(deviceRegistrationRepository.findAllByTeamId(teamId)).thenReturn(List.of(main));
        // Invite is valid but scoped to a different team — must not bypass this team's gate.
        when(authService.validateTeamInviteToken("wrong-team"))
            .thenReturn(Optional.of(new TeamInvite(gameId, UUID.randomUUID())));

        assertThrows(CheckInWindowClosedException.class,
            () -> service.registerDevice(gameId, teamId, null, "Late phone", "wrong-team"));
    }

    @Test
    void resumedDevice_afterWindow_isAllowed() {
        DeviceRegistration existing = existingDevice();
        team.setMainDeviceId(UUID.randomUUID());
        physicallyCheckedIn(20);
        // Client's deviceId hint matches an existing row for this team -> idempotent resume,
        // both check-in gates are skipped entirely.
        when(deviceRegistrationRepository.findByTeamIdAndId(teamId, existing.getId()))
            .thenReturn(Optional.of(existing));

        RegisterDeviceResponse response =
            service.registerDevice(gameId, teamId, existing.getId(), "same phone", null);

        assertEquals(existing.getId(), response.deviceId());
    }

    @Test
    void acceptingNewDevicesByTeam_reflectsCheckInWindow() {
        // Team A: not physically checked in -> not accepting (must see the rally desk).
        Team a = new Team(teamId, gameId, "A", "");
        // Team B: checked in 5 min ago -> accepting.
        UUID teamBId = UUID.randomUUID();
        Team b = new Team(teamBId, gameId, "B", "");
        b.setCheckedIn(true);
        b.setCheckedInAt(Instant.now().minus(5, ChronoUnit.MINUTES));
        // Team C: checked in 30 min ago -> window closed.
        UUID teamCId = UUID.randomUUID();
        Team c = new Team(teamCId, gameId, "C", "");
        c.setCheckedIn(true);
        c.setCheckedInAt(Instant.now().minus(30, ChronoUnit.MINUTES));

        when(teamRepository.findByGameId(gameId)).thenReturn(List.of(a, b, c));

        Map<UUID, Boolean> result = service.acceptingNewDevicesByTeam(gameId);

        assertFalse(result.get(teamId));  // A, not checked in
        assertTrue(result.get(teamBId));  // B within window
        assertFalse(result.get(teamCId)); // C past window
    }
}
