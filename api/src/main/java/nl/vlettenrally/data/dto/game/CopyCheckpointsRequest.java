package nl.vlettenrally.data.dto.game;

import io.quarkus.runtime.annotations.RegisterForReflection;

import java.util.UUID;

/**
 * Request to copy all zones (checkpoints) from another game into the game
 * identified by the path.
 */
@RegisterForReflection
public record CopyCheckpointsRequest(UUID sourceGameId) {}
