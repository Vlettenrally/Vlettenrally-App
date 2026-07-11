package nl.vlettenrally.service.impl;

import io.quarkus.logging.Log;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import nl.vlettenrally.data.domain.DeviceRegistration;
import nl.vlettenrally.data.domain.Game;
import nl.vlettenrally.data.domain.Team;
import nl.vlettenrally.data.dto.auth.CheckInStateResponse;
import nl.vlettenrally.data.dto.auth.DeviceResponse;
import nl.vlettenrally.data.dto.auth.RegisterDeviceResponse;
import nl.vlettenrally.data.dto.auth.TokenResult;
import nl.vlettenrally.data.repository.DeviceRegistrationRepository;
import nl.vlettenrally.data.repository.GameRepository;
import nl.vlettenrally.data.repository.TeamRepository;
import nl.vlettenrally.service.AuthService;
import nl.vlettenrally.service.CheckInCoordinationService;
import nl.vlettenrally.service.CheckInWindowClosedException;
import nl.vlettenrally.websockets.WebSocketPublisher;
import nl.vlettenrally.websockets.dto.WebSocketMessageType;
import org.eclipse.microprofile.config.inject.ConfigProperty;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

@ApplicationScoped
public class CheckInCoordinationServiceImpl implements CheckInCoordinationService {

    @ConfigProperty(name = "vlettenrally.timezone", defaultValue = "Europe/Amsterdam")
    ZoneId zoneId;

    @ConfigProperty(name = "vlettenrally.checkin.max-devices-per-team", defaultValue = "10")
    int maxDevicesPerTeam;

    @ConfigProperty(name = "vlettenrally.checkin.additional-device-window-minutes", defaultValue = "15")
    int additionalDeviceWindowMinutes;

    @Inject
    GameRepository gameRepository;

    @Inject
    TeamRepository teamRepository;

    @Inject
    DeviceRegistrationRepository deviceRegistrationRepository;

    @Inject
    AuthService authService;

    @Inject
    WebSocketPublisher webSocketPublisher;

    @Inject
    nl.vlettenrally.config.TraccarConfig traccarConfig;

    @Override
    public List<Game> findCheckInGamesForToday() {
        LocalDate today = LocalDate.now(zoneId);
        Instant now = Instant.now();
        return gameRepository.findAll().stream()
            .filter(g -> g.getStartTime() != null)
            .filter(g -> g.getStatus() == null || g.getStatus() != Game.GameStatus.COMPLETED)
            .filter(g -> {
                boolean isToday = g.getStartTime().atZone(zoneId).toLocalDate().equals(today);
                boolean checkInOpen = g.getCheckInStartTime() != null
                    && !now.isBefore(g.getCheckInStartTime());
                return isToday || checkInOpen;
            })
            .sorted(Comparator.comparing(Game::getStartTime))
            .toList();
    }

    @Override
    public RegisterDeviceResponse registerDevice(UUID gameId, UUID teamId, UUID deviceIdHint, String nickname, String inviteToken) {
        if (gameId == null || teamId == null) {
            throw new IllegalArgumentException("gameId and teamId are required");
        }
        String normalizedNickname = nickname == null ? "" : nickname.trim();

        Game game = gameRepository.findById(gameId)
            .orElseThrow(() -> new IllegalArgumentException("Game not found: " + gameId));

        Team team = teamRepository.findById(teamId)
            .orElseThrow(() -> new IllegalArgumentException("Team not found: " + teamId));

        if (!game.getId().equals(team.getGameId())) {
            throw new IllegalArgumentException("Team does not belong to game");
        }

        // The client's deviceIdHint is only trusted for idempotency: if a row with
        // that id already belongs to this team, we resume it. Otherwise we ignore
        // the hint and allocate a fresh server-side UUID. This stops a hostile
        // client from spawning rows under arbitrary ids of its choosing.
        Optional<DeviceRegistration> existing = deviceIdHint == null
            ? Optional.empty()
            : deviceRegistrationRepository.findByTeamIdAndId(teamId, deviceIdHint);

        DeviceRegistration device;
        if (existing.isPresent()) {
            device = existing.get();
        } else {
            // New registration.
            List<DeviceRegistration> teamDevices = deviceRegistrationRepository.findAllByTeamId(teamId);

            // Additional-device gate: once the team has a main device, a brand-new device
            // is an "additional phone". Via the regular game/team picker it may only join
            // within the window after the team's initial check-in; a valid invite token
            // (the team/admin QR path) always bypasses the gate.
            if (team.getMainDeviceId() != null && !inviteGrantsAccess(inviteToken, teamId)) {
                Instant initialCheckInAt = teamDevices.stream()
                    .map(DeviceRegistration::getCreatedAt)
                    .filter(Objects::nonNull)
                    .min(Comparator.naturalOrder())
                    .orElse(null);
                if (initialCheckInAt != null
                    && Instant.now().isAfter(initialCheckInAt.plus(additionalDeviceWindowMinutes, ChronoUnit.MINUTES))) {
                    throw new CheckInWindowClosedException(
                        "This team's check-in window has closed. Ask your team to share their QR code, or ask an admin.");
                }
            }

            // Enforce the per-team cap.
            if (teamDevices.size() >= maxDevicesPerTeam) {
                throw new IllegalStateException("Team has reached the maximum number of registered devices ("
                    + maxDevicesPerTeam + "). Ask an admin to remove an unused device.");
            }
            UUID assignedId = UUID.randomUUID();
            device = new DeviceRegistration(assignedId, teamId, gameId, normalizedNickname);
        }
        device.setNickname(normalizedNickname);
        device.setGameId(gameId);
        device.touch();
        deviceRegistrationRepository.save(device);

        UUID assignedDeviceId = device.getId();

        // Atomically claim the main-device slot if it's still open.
        boolean wasMainAssignment = false;
        if (team.getMainDeviceId() == null) {
            team.setMainDeviceId(assignedDeviceId);
            team.setUpdatedAt(Instant.now());
            team = teamRepository.save(team);
            wasMainAssignment = true;
        }

        boolean isMainDevice = assignedDeviceId.equals(team.getMainDeviceId());
        boolean detailsSubmitted = team.isCheckedInDigitally();

        String mainDeviceNickname = null;
        if (team.getMainDeviceId() != null) {
            Optional<DeviceRegistration> mainDevice = deviceRegistrationRepository
                .findByTeamIdAndId(teamId, team.getMainDeviceId());
            mainDeviceNickname = mainDevice.map(DeviceRegistration::getNickname).orElse(null);
        }

        TokenResult tokens = authService.issueTeamTokens(team, assignedDeviceId);

        // Only the main device sets up the team's single Traccar tracker.
        boolean traccarEnabled = isMainDevice
            && team.isTraccarEnabled()
            && team.getTraccarIdentifier() != null;
        String traccarIdentifier = traccarEnabled ? team.getTraccarIdentifier() : null;
        String traccarServerUrl = traccarEnabled ? traccarConfig.publicUrl() : null;

        Log.infof("Device registered for check-in - teamId: %s, deviceId: %s, isMain: %s, nickname: %s, mainAssigned: %s, hinted: %s, traccar: %s",
            teamId, assignedDeviceId, isMainDevice, normalizedNickname, wasMainAssignment, existing.isPresent(), traccarEnabled);

        return new RegisterDeviceResponse(
            assignedDeviceId,
            tokens.accessToken(),
            tokens.refreshToken(),
            tokens.expiresIn(),
            isMainDevice,
            detailsSubmitted,
            mainDeviceNickname,
            traccarEnabled,
            traccarIdentifier,
            traccarServerUrl
        );
    }

    @Override
    public Map<UUID, Boolean> acceptingNewDevicesByTeam(UUID gameId) {
        Instant now = Instant.now();

        // Earliest device registration per team = the team's "initial check-in" time.
        // One scan over the device table rather than a per-team query.
        Map<UUID, Instant> earliestByTeam = new HashMap<>();
        for (DeviceRegistration d : deviceRegistrationRepository.findAll()) {
            if (d.getTeamId() == null || !gameId.equals(d.getGameId()) || d.getCreatedAt() == null) {
                continue;
            }
            earliestByTeam.merge(d.getTeamId(), d.getCreatedAt(),
                (a, b) -> a.isBefore(b) ? a : b);
        }

        Map<UUID, Boolean> result = new HashMap<>();
        for (Team team : teamRepository.findByGameId(gameId)) {
            boolean accepting;
            if (team.getMainDeviceId() == null) {
                accepting = true; // No main device yet — first phone can always join.
            } else {
                Instant earliest = earliestByTeam.get(team.getId());
                accepting = earliest == null
                    || !now.isAfter(earliest.plus(additionalDeviceWindowMinutes, ChronoUnit.MINUTES));
            }
            result.put(team.getId(), accepting);
        }
        return result;
    }

    /** True only when the invite token is present, valid, and scoped to this exact team. */
    private boolean inviteGrantsAccess(String inviteToken, UUID teamId) {
        if (inviteToken == null || inviteToken.isBlank()) {
            return false;
        }
        return authService.validateTeamInviteToken(inviteToken)
            .map(invite -> teamId.equals(invite.teamId()))
            .orElse(false);
    }

    @Override
    public CheckInStateResponse getState(UUID teamId) {
        Team team = teamRepository.findById(teamId)
            .orElseThrow(() -> new IllegalArgumentException("Team not found: " + teamId));

        List<DeviceRegistration> devices = deviceRegistrationRepository.findAllByTeamId(teamId);
        UUID mainDeviceId = team.getMainDeviceId();

        List<DeviceResponse> deviceResponses = devices.stream()
            .map(d -> new DeviceResponse(
                d.getId(),
                d.getNickname(),
                d.getId().equals(mainDeviceId),
                d.getCreatedAt(),
                d.getLastSeen()
            ))
            .toList();

        String mainDeviceNickname = devices.stream()
            .filter(d -> d.getId().equals(mainDeviceId))
            .map(DeviceRegistration::getNickname)
            .findFirst()
            .orElse(null);

        return new CheckInStateResponse(
            mainDeviceId,
            mainDeviceNickname,
            team.isCheckedInDigitally(),
            deviceResponses
        );
    }

    @Override
    public void resetTeam(UUID teamId) {
        Team team = teamRepository.findById(teamId)
            .orElseThrow(() -> new IllegalArgumentException("Team not found: " + teamId));

        deviceRegistrationRepository.deleteByTeamId(teamId);

        team.setMainDeviceId(null);
        team.setCheckedIn(false);
        team.setCheckedInDigitally(false);
        team.setParticipants(new ArrayList<>());
        team.setEmergencyPhoneNumbers(new ArrayList<>());
        team.setUpdatedAt(Instant.now());
        teamRepository.save(team);

        authService.revokeTokens(teamId);

        if (team.getGameId() != null) {
            webSocketPublisher.sendToTeam(
                team.getGameId(),
                team.getId(),
                WebSocketMessageType.CHECKIN_RESET.getValue(),
                Map.of("teamId", team.getId().toString())
            );
        }

        Log.infof("Check-in reset for team %s", teamId);
    }

    @Override
    public void removeDevice(UUID teamId, UUID deviceId) {
        Team team = teamRepository.findById(teamId)
            .orElseThrow(() -> new IllegalArgumentException("Team not found: " + teamId));

        DeviceRegistration device = deviceRegistrationRepository.findByTeamIdAndId(teamId, deviceId)
            .orElseThrow(() -> new IllegalArgumentException("Device not registered to team: " + deviceId));

        deviceRegistrationRepository.delete(device.getId());

        // If we just removed the main device, pick the next-oldest as new main
        // (or clear if no devices remain).
        boolean wasMain = deviceId.equals(team.getMainDeviceId());
        UUID newMainDeviceId = team.getMainDeviceId();
        String newMainNickname = null;
        if (wasMain) {
            List<DeviceRegistration> remaining = deviceRegistrationRepository.findAllByTeamId(teamId);
            if (remaining.isEmpty()) {
                team.setMainDeviceId(null);
                newMainDeviceId = null;
            } else {
                DeviceRegistration next = remaining.getFirst();
                team.setMainDeviceId(next.getId());
                newMainDeviceId = next.getId();
                newMainNickname = next.getNickname();
            }
            team.setUpdatedAt(Instant.now());
            teamRepository.save(team);
        }

        authService.revokeDeviceTokens(teamId, deviceId);

        if (team.getGameId() != null) {
            webSocketPublisher.sendToTeam(
                team.getGameId(),
                team.getId(),
                WebSocketMessageType.CHECKIN_DEVICE_REMOVED.getValue(),
                Map.of(
                    "teamId", team.getId().toString(),
                    "deviceId", deviceId.toString(),
                    "newMainDeviceId", newMainDeviceId == null ? "" : newMainDeviceId.toString(),
                    "newMainDeviceNickname", newMainNickname == null ? "" : newMainNickname
                )
            );
        }

        Log.infof("Device removed from team %s -> deviceId %s (wasMain=%s, newMain=%s)",
            teamId, deviceId, wasMain, newMainDeviceId);
    }

    @Override
    public void setMainDevice(UUID teamId, UUID deviceId) {
        Team team = teamRepository.findById(teamId)
            .orElseThrow(() -> new IllegalArgumentException("Team not found: " + teamId));

        DeviceRegistration device = deviceRegistrationRepository.findByTeamIdAndId(teamId, deviceId)
            .orElseThrow(() -> new IllegalArgumentException("Device not registered to team: " + deviceId));

        team.setMainDeviceId(device.getId());
        team.setUpdatedAt(Instant.now());
        teamRepository.save(team);

        if (team.getGameId() != null) {
            webSocketPublisher.sendToTeam(
                team.getGameId(),
                team.getId(),
                WebSocketMessageType.CHECKIN_MAIN_CHANGED.getValue(),
                Map.of(
                    "teamId", team.getId().toString(),
                    "mainDeviceId", device.getId().toString(),
                    "mainDeviceNickname", device.getNickname() == null ? "" : device.getNickname()
                )
            );
        }

        Log.infof("Main device changed for team %s -> device %s", teamId, deviceId);
    }
}
