package nl.vlettenrally.data.dto.points;

import io.quarkus.runtime.annotations.RegisterForReflection;

import java.time.Instant;
import java.util.UUID;

/**
 * Response DTO for a single ledger entry.
 * <p>
 * The frontend renders "deducted" as {@code originalAmount - currentAmount} when positive,
 * so both values are sent on every payload.
 */
@RegisterForReflection
public record PointEntryResponse(
    UUID id,
    UUID teamId,
    UUID gameId,
    String entryType,
    int originalAmount,
    int currentAmount,
    UUID sourceAchievementId,
    Double lat,
    Double lng,
    String description,
    Instant createdAt,
    Instant updatedAt,
    UUID createdBy,
    Instant editedAt,
    UUID editedBy,
    String editReason
) {}
