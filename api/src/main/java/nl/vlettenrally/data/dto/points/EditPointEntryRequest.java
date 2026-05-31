package nl.vlettenrally.data.dto.points;

import io.quarkus.runtime.annotations.RegisterForReflection;

/**
 * Admin request body to edit a ledger entry's current amount.
 * {@code reason} is required to preserve the audit trail.
 */
@RegisterForReflection
public record EditPointEntryRequest(
    int amount,
    String reason
) {}
