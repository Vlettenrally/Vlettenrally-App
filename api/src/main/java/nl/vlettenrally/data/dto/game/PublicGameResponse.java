package nl.vlettenrally.data.dto.game;

import java.util.UUID;

import io.quarkus.runtime.annotations.RegisterForReflection;
import nl.vlettenrally.data.domain.Game;

/**
 * A game as exposed by the public gallery API, so a consumer can pick one to fetch pictures for.
 * <p>
 * A deliberately minimal projection: unlike {@link GameResponse} it omits the location endpoint
 * URLs, polling config and other operational fields that should not be public.
 */
@RegisterForReflection
public record PublicGameResponse(
    UUID id,
    String name,
    String startTime,
    String status,
    // Whether scores are actually fetchable on the public scoreboard API. Matches the availability
    // gate in PublicRankingResource: true when the public scoreboard is enabled
    // (publicRankingsEnabled, SET_PUBLIC_RANKINGS_ENABLED) OR the ranking has been revealed in-app
    // (rankingsPublished, SET_RANKINGS_PUBLISHED), which also opens the public endpoint.
    boolean scoresPublished
) {
    public static PublicGameResponse from(Game g) {
        return new PublicGameResponse(
            g.getId(),
            g.getName(),
            g.getStartTime() != null ? g.getStartTime().toString() : null,
            g.getStatus() != null ? g.getStatus().name().toLowerCase() : null,
            Boolean.TRUE.equals(g.getPublicRankingsEnabled()) || Boolean.TRUE.equals(g.getRankingsPublished())
        );
    }
}
