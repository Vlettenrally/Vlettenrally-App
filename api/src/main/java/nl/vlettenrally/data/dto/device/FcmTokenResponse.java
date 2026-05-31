package nl.vlettenrally.data.dto.device;

import io.quarkus.runtime.annotations.RegisterForReflection;

import java.util.UUID;

/**
 * Response DTO for FCM token registration.
 *
 * Includes the server-assigned {@code deviceId} so the client can persist it for
 * future calls and treat its localStorage value as a resumption hint rather than
 * the source of truth.
 */
@RegisterForReflection
public record FcmTokenResponse(boolean success, String message, UUID deviceId) {

    public static FcmTokenResponse ok() {
        return new FcmTokenResponse(true, "FCM token registered successfully", null);
    }

    public static FcmTokenResponse ok(String message) {
        return new FcmTokenResponse(true, message, null);
    }

    public static FcmTokenResponse ok(UUID deviceId) {
        return new FcmTokenResponse(true, "FCM token registered successfully", deviceId);
    }
}
