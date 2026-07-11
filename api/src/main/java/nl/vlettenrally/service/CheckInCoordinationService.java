package nl.vlettenrally.service;

import nl.vlettenrally.data.domain.Game;
import nl.vlettenrally.data.dto.auth.CheckInStateResponse;
import nl.vlettenrally.data.dto.auth.RegisterDeviceResponse;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Multi-device check-in coordination.
 * <p>
 * Public check-in is keyed on a localStorage-generated device UUID. The first device
 * to register for a team becomes the "main device" and fills participants + emergency
 * phone numbers; subsequent devices wait. Admins can reset the team's coordination
 * or hand main-device status to another registered device.
 */
public interface CheckInCoordinationService {

    /**
     * Games eligible for self-service check-in: not yet COMPLETED, and either starting
     * today (Europe/Amsterdam) or with a configured {@code checkInStartTime} that has
     * already passed. Sorted by startTime.
     */
    List<Game> findCheckInGamesForToday();

    /**
     * Register a device for a team and issue team access + refresh tokens.
     * Idempotent on (teamId, deviceId). Atomically claims the main-device slot
     * if no device has claimed it yet.
     * <p>
     * Check-in gates (new registrations only; resumed idempotent registrations are never
     * gated): a device may register only for a team that has been physically checked in
     * at the rally desk — otherwise {@link TeamNotCheckedInException}. Once checked in, a
     * new device (first or additional) registering more than the configured window after
     * that check-in is rejected with {@link CheckInWindowClosedException} — UNLESS a valid
     * {@code inviteToken} for this team is supplied (the team/admin QR path), which
     * bypasses the time window (but not the physical-check-in requirement).
     *
     * @param inviteToken optional signed team-invite token from a scanned QR; null for
     *                    the regular game/team selection path
     */
    RegisterDeviceResponse registerDevice(UUID gameId, UUID teamId, UUID deviceId, String nickname, String inviteToken);

    /**
     * Current coordination state for a team — main device, whether the details step
     * is complete, and the list of registered devices in registration order.
     */
    CheckInStateResponse getState(UUID teamId);

    /**
     * For every team in the game, whether the regular game/team selection screen may
     * still be used to register a device: {@code true} only when the team has been
     * physically checked in AND that check-in was within the configured window;
     * {@code false} before physical check-in or once the window has closed (such teams
     * are then only joinable via the invite QR or an admin). Keyed by teamId.
     */
    Map<UUID, Boolean> acceptingNewDevicesByTeam(UUID gameId);

    /**
     * Admin action: wipe every device for the team, clear participants + emergency
     * phone numbers, reset check-in flags, revoke team refresh tokens. Publishes
     * CHECKIN_RESET so any open participant tabs bounce back to /check-in.
     */
    void resetTeam(UUID teamId);

    /**
     * Admin action: hand main-device status to a different registered device.
     * Publishes CHECKIN_MAIN_CHANGED.
     */
    void setMainDevice(UUID teamId, UUID deviceId);

    /**
     * Admin action: remove a single registered device from a team. Revokes that
     * device's access tokens, deletes the row, demotes main-device status to the
     * next-oldest device when applicable, and publishes CHECKIN_DEVICE_REMOVED so
     * the affected participant tab can self-eject.
     */
    void removeDevice(UUID teamId, UUID deviceId);
}
