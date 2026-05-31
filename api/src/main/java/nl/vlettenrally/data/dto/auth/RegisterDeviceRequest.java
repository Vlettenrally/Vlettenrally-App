package nl.vlettenrally.data.dto.auth;

import io.quarkus.runtime.annotations.RegisterForReflection;

import java.util.UUID;

/**
 * deviceId is only a HINT: the backend trusts it to resume an existing registration
 * if it already owns a row with that id for this team, otherwise it ignores the
 * value and allocates a fresh server-side UUID. This prevents a hostile client from
 * spawning rows under arbitrary ids of its choosing.
 */
@RegisterForReflection
public record RegisterDeviceRequest(
    UUID gameId,
    UUID teamId,
    UUID deviceId,
    String nickname
) {}
