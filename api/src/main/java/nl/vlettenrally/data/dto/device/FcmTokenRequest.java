package nl.vlettenrally.data.dto.device;

import io.quarkus.runtime.annotations.RegisterForReflection;

import java.util.UUID;

/**
 * Request DTO for registering an FCM token.
 *
 * deviceId is the participant's localStorage UUID — used to attach the token to the
 * correct DeviceRegistration row when a team has multiple devices. Optional for
 * back-compat with older clients; falls back to the team's first device.
 */
@RegisterForReflection
public record FcmTokenRequest(String token, UUID deviceId, String nickname) {
    public FcmTokenRequest(String token) {
        this(token, null, null);
    }

    public FcmTokenRequest(String token, UUID deviceId) {
        this(token, deviceId, null);
    }
}
