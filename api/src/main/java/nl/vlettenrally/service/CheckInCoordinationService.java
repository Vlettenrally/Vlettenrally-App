package nl.vlettenrally.service;

import nl.vlettenrally.data.domain.Game;
import nl.vlettenrally.data.dto.auth.CheckInStateResponse;
import nl.vlettenrally.data.dto.auth.RegisterDeviceResponse;

import java.util.List;
import java.util.UUID;

/**
 * Multi-device check-in coordination.
 *
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
     */
    RegisterDeviceResponse registerDevice(UUID gameId, UUID teamId, UUID deviceId, String nickname);

    /**
     * Current coordination state for a team — main device, whether the details step
     * is complete, and the list of registered devices in registration order.
     */
    CheckInStateResponse getState(UUID teamId);

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
