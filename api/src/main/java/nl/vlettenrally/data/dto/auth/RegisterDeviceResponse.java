package nl.vlettenrally.data.dto.auth;

import io.quarkus.runtime.annotations.RegisterForReflection;

@RegisterForReflection
public record RegisterDeviceResponse(
    java.util.UUID deviceId,
    String accessToken,
    String refreshToken,
    long expiresIn,
    String tokenType,
    boolean isMainDevice,
    boolean detailsSubmitted,
    String mainDeviceNickname,
    // Traccar setup data — only populated for the main device of a Traccar-enabled
    // team, so the check-in flow can show the app configuration step.
    boolean traccarEnabled,
    String traccarIdentifier,
    String traccarServerUrl
) {
    public RegisterDeviceResponse(java.util.UUID deviceId, String accessToken, String refreshToken, long expiresIn,
                                  boolean isMainDevice, boolean detailsSubmitted, String mainDeviceNickname,
                                  boolean traccarEnabled, String traccarIdentifier, String traccarServerUrl) {
        this(deviceId, accessToken, refreshToken, expiresIn, "Bearer", isMainDevice, detailsSubmitted,
            mainDeviceNickname, traccarEnabled, traccarIdentifier, traccarServerUrl);
    }
}
