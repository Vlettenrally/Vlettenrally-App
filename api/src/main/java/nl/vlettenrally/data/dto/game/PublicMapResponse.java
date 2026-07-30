package nl.vlettenrally.data.dto.game;

import io.quarkus.runtime.annotations.RegisterForReflection;

import java.util.List;
import java.util.UUID;

/**
 * Everything the public narrowcasting map draws, in one payload.
 * <p>
 * Teams and zones are bundled rather than split across two endpoints because the consumer is a
 * display polling on a fixed interval: one request per tick keeps the token check, the cache lookup
 * and the refresh cadence in a single place.
 * <p>
 * Team positions reuse {@link TeamLocationResponse}, the same shape the admin map and the
 * {@code LOCATION_UPDATE} websocket event already carry, so the map client needs no second mapping.
 * Zones are the trimmed {@link PublicMapZoneResponse}, and hidden ones never appear.
 */
@RegisterForReflection
public record PublicMapResponse(
    UUID gameId,
    String gameName,
    List<TeamLocationResponse> teams,
    List<PublicMapZoneResponse> zones
) {
}
