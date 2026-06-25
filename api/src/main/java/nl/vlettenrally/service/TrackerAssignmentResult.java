package nl.vlettenrally.service;

import java.util.List;

/**
 * Result of auto-assigning a batch of tracker IDs to a game's teams.
 *
 * @param assigned               number of trackers newly assigned to teams
 * @param teamsWithoutTracker    number of teams still without a tracker afterwards
 * @param unused                 number of pasted tracker IDs left over (more trackers than free teams)
 * @param assignedPairs          human-readable "trackerId -> boatNumber" lines for the assignments made
 * @param teamsWithoutTrackerNames boat numbers of teams still without a tracker
 * @param unusedTrackers         leftover tracker IDs that could not be assigned
 */
public record TrackerAssignmentResult(
        int assigned,
        int teamsWithoutTracker,
        int unused,
        List<String> assignedPairs,
        List<String> teamsWithoutTrackerNames,
        List<String> unusedTrackers
) {}
