package nl.vlettenrally.data.dto.device;

import io.quarkus.runtime.annotations.RegisterForReflection;

/**
 * Request DTO for registering an FCM token
 */
@RegisterForReflection
public record FcmTokenRequest(String token) {}
