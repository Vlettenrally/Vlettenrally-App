package nl.vlettenrally.data.dto.automation;

import io.quarkus.runtime.annotations.RegisterForReflection;

import java.util.List;

/**
 * Outcome summary of a copy-automations operation.
 *
 * @param copied                      number of rules created in the target game
 * @param skippedDuplicate            names of source rules skipped because a rule
 *                                    with that name already exists in the target
 * @param skippedUnmatchedCheckpoint  names of checkpoint rules skipped because
 *                                    their checkpoint could not be matched in the
 *                                    target game
 * @param distanceRateCopied          whether the source distance-points rate was
 *                                    applied to the target's system rule
 */
@RegisterForReflection
public record CopyAutomationsResult(
    int copied,
    List<String> skippedDuplicate,
    List<String> skippedUnmatchedCheckpoint,
    boolean distanceRateCopied
) {}
