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
    String startTime
) {
    public static PublicGameResponse from(Game g) {
        return new PublicGameResponse(
            g.getId(),
            g.getName(),
            g.getStartTime() != null ? g.getStartTime().toString() : null
        );
    }
}
