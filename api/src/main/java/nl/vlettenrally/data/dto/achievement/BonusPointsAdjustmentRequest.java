package nl.vlettenrally.data.dto.achievement;

import io.quarkus.runtime.annotations.RegisterForReflection;

/**
 * Add (or subtract) bonus points on an already-VERIFIED achievement. Used by
 * checkpoint personnel to reward on-the-spot extras after auto-verification.
 */
@RegisterForReflection
public record BonusPointsAdjustmentRequest(
        int bonusDelta
) {}
