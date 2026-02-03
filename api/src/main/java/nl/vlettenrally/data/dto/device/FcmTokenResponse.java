package nl.vlettenrally.data.dto.device;

import io.quarkus.runtime.annotations.RegisterForReflection;

/**
 * Response DTO for FCM token registration
 */
@RegisterForReflection
public record FcmTokenResponse(boolean success, String message) {
    
    public static FcmTokenResponse ok() {
        return new FcmTokenResponse(true, "FCM token registered successfully");
    }
    
    public static FcmTokenResponse ok(String message) {
        return new FcmTokenResponse(true, message);
    }
}
