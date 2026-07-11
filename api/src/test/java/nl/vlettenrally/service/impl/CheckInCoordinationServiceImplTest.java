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

/**
 * Unit tests for the additional-device time gate in registerDevice, plus the
 * per-team acceptingNewDevices computation. The first phone and resumed devices are
 * never gated; extra phones through the regular picker are gated after the window,
 * but a valid invite token (the team/admin QR path) always bypasses the gate.
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
        service.additionalDeviceWindowMinutes = 15;
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

    private DeviceRegistration deviceRegisteredAgo(long minutes) {
        DeviceRegistration d = new DeviceRegistration(UUID.randomUUID(), teamId, gameId, "main");
        d.setCreatedAt(Instant.now().minus(minutes, ChronoUnit.MINUTES));
        return d;
    }

    @Test
    void firstDevice_isAllowed_andClaimsMain() {
        // No main device yet, no existing devices.
        when(deviceRegistrationRepository.findAllByTeamId(teamId)).thenReturn(List.of());

        RegisterDeviceResponse response =
            service.registerDevice(gameId, teamId, null, "Lisa's phone", null);

        assertTrue(response.isMainDevice());
        assertEquals(response.deviceId(), team.getMainDeviceId());
    }

    @Test
    void additionalDevice_withinWindow_isAllowed() {
        DeviceRegistration main = deviceRegisteredAgo(5);
        team.setMainDeviceId(main.getId());
        when(deviceRegistrationRepository.findAllByTeamId(teamId)).thenReturn(List.of(main));
        when(deviceRegistrationRepository.findByTeamIdAndId(teamId, main.getId()))
            .thenReturn(Optional.of(main));

        RegisterDeviceResponse response =
            service.registerDevice(gameId, teamId, null, "Sanne's phone", null);

        assertFalse(response.isMainDevice());
    }

    @Test
    void additionalDevice_afterWindow_isRejected() {
        DeviceRegistration main = deviceRegisteredAgo(20);
        team.setMainDeviceId(main.getId());
        when(deviceRegistrationRepository.findAllByTeamId(teamId)).thenReturn(List.of(main));

        assertThrows(CheckInWindowClosedException.class,
            () -> service.registerDevice(gameId, teamId, null, "Late phone", null));
    }

    @Test
    void additionalDevice_afterWindow_withValidInvite_isAllowed() {
        DeviceRegistration main = deviceRegisteredAgo(20);
        team.setMainDeviceId(main.getId());
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
    void additionalDevice_afterWindow_withInviteForOtherTeam_isRejected() {
        DeviceRegistration main = deviceRegisteredAgo(20);
        team.setMainDeviceId(main.getId());
        when(deviceRegistrationRepository.findAllByTeamId(teamId)).thenReturn(List.of(main));
        // Invite is valid but scoped to a different team — must not bypass this team's gate.
        when(authService.validateTeamInviteToken("wrong-team"))
            .thenReturn(Optional.of(new TeamInvite(gameId, UUID.randomUUID())));

        assertThrows(CheckInWindowClosedException.class,
            () -> service.registerDevice(gameId, teamId, null, "Late phone", "wrong-team"));
    }

    @Test
    void resumedDevice_afterWindow_isAllowed() {
        DeviceRegistration existing = deviceRegisteredAgo(20);
        team.setMainDeviceId(UUID.randomUUID());
        // Client's deviceId hint matches an existing row for this team -> idempotent resume,
        // gate is skipped entirely.
        when(deviceRegistrationRepository.findByTeamIdAndId(teamId, existing.getId()))
            .thenReturn(Optional.of(existing));

        RegisterDeviceResponse response =
            service.registerDevice(gameId, teamId, existing.getId(), "same phone", null);

        assertEquals(existing.getId(), response.deviceId());
    }

    @Test
    void acceptingNewDevicesByTeam_reflectsMainAndWindow() {
        // Team A: no main device -> always accepting.
        Team a = new Team(teamId, gameId, "A", "");
        // Team B: main device, initial check-in 5 min ago -> still accepting.
        UUID teamBId = UUID.randomUUID();
        Team b = new Team(teamBId, gameId, "B", "");
        b.setMainDeviceId(UUID.randomUUID());
        // Team C: main device, initial check-in 30 min ago -> window closed.
        UUID teamCId = UUID.randomUUID();
        Team c = new Team(teamCId, gameId, "C", "");
        c.setMainDeviceId(UUID.randomUUID());

        DeviceRegistration bDev = new DeviceRegistration(UUID.randomUUID(), teamBId, gameId, "b");
        bDev.setCreatedAt(Instant.now().minus(5, ChronoUnit.MINUTES));
        DeviceRegistration cDev = new DeviceRegistration(UUID.randomUUID(), teamCId, gameId, "c");
        cDev.setCreatedAt(Instant.now().minus(30, ChronoUnit.MINUTES));

        when(teamRepository.findByGameId(gameId)).thenReturn(List.of(a, b, c));
        when(deviceRegistrationRepository.findAll()).thenReturn(List.of(bDev, cDev));

        Map<UUID, Boolean> result = service.acceptingNewDevicesByTeam(gameId);

        assertTrue(result.get(teamId));   // A
        assertTrue(result.get(teamBId));  // B within window
        assertFalse(result.get(teamCId)); // C past window
    }
}
