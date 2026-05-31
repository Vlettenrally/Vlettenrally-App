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
    String mainDeviceNickname
) {
    public RegisterDeviceResponse(java.util.UUID deviceId, String accessToken, String refreshToken, long expiresIn,
                                  boolean isMainDevice, boolean detailsSubmitted, String mainDeviceNickname) {
        this(deviceId, accessToken, refreshToken, expiresIn, "Bearer", isMainDevice, detailsSubmitted, mainDeviceNickname);
    }
}
