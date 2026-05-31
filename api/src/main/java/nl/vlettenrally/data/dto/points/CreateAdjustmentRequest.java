package nl.vlettenrally.data.dto.points;

import io.quarkus.runtime.annotations.RegisterForReflection;

import java.util.UUID;

/**
 * Admin request body to create a MANUAL_ADJUSTMENT entry.
 * {@code amount} is signed: negative deducts, positive awards.
 */
@RegisterForReflection
public record CreateAdjustmentRequest(
    UUID teamId,
    UUID gameId,
    int amount,
    String description
) {}
