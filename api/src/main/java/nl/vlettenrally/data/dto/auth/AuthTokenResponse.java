package nl.vlettenrally.data.dto.auth;

import io.quarkus.runtime.annotations.RegisterForReflection;

@RegisterForReflection
public record AuthTokenResponse(
    String accessToken,
    String refreshToken,
    long expiresIn,
    String tokenType
) {
    public AuthTokenResponse(String accessToken, String refreshToken, long expiresIn) {
        this(accessToken, refreshToken, expiresIn, "Bearer");
    }
}