package nl.vlettenrally.data.dto.game;

import io.quarkus.runtime.annotations.RegisterForReflection;

/**
 * The per-game token an admin hands to a narrowcasting display, as returned by
 * {@code GET /api/v1/games/{gameId}/public-map-token}.
 * <p>
 * Deliberately just the token: the display URL is assembled client-side from the admin app's own
 * origin, so the API never needs to know where the frontend is hosted.
 */
@RegisterForReflection
public record PublicMapTokenResponse(String token) {
}
