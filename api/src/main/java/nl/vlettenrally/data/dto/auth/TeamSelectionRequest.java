package nl.vlettenrally.data.dto.auth;

import io.quarkus.runtime.annotations.RegisterForReflection;
import java.util.UUID;

/**
 * Request to select a team from a game-scoped check-in token.
 * Used when a user with a game-scoped token selects their team before completing check-in.
 * 
 * @param teamId The ID of the team the user is selecting
 */
@RegisterForReflection
public record TeamSelectionRequest(
    UUID teamId
) {}
