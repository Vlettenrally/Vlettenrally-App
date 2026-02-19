package nl.vlettenrally.data.dto.auth;

import io.quarkus.runtime.annotations.RegisterForReflection;

/**
 * Response containing a WebSocket connection token.
 */
@RegisterForReflection
public record WebSocketTokenResponse(String token, int expiresInSeconds) {}

