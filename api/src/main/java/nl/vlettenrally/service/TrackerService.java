package nl.vlettenrally.service;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import nl.vlettenrally.data.domain.Game;
import nl.vlettenrally.data.domain.Team;
import nl.vlettenrally.data.domain.Tracker;
import nl.vlettenrally.data.repository.GameRepository;
import nl.vlettenrally.data.repository.TeamRepository;
import nl.vlettenrally.data.repository.TrackerRepository;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Manages the global tracker pool: bulk-adding tracker IDs, enabling/disabling
 * trackers, and listing them decorated with their current assignment. Assignment
 * itself is handled by {@link TrackerAssignmentService}.
 */
@ApplicationScoped
public class TrackerService {

    @Inject
    TrackerRepository trackerRepository;

    @Inject
    TeamRepository teamRepository;

    @Inject
    GameRepository gameRepository;

    /** One team — game pair holding a tracker. */
    public record Assignment(String teamName, String gameName) {}

    /**
     * A tracker with every team/game currently holding it (across non-completed
     * games; the same tracker may be assigned in multiple games), for the pool
     * overview.
     */
    public record TrackerWithAssignments(Tracker tracker, List<Assignment> assignments) {}

    /** Result of a bulk add: how many IDs were new vs. already in the pool. */
    public record AddResult(int added, int duplicates) {}

    /**
     * Add a pasted batch of tracker IDs to the global pool. IDs are trimmed and
     * de-duplicated; ones already in the pool are counted as duplicates.
     */
    @Transactional
    public AddResult addTrackers(List<String> rawTrackerIds) {
        LinkedHashSet<String> normalized = new LinkedHashSet<>();
        if (rawTrackerIds != null) {
            for (String raw : rawTrackerIds) {
                if (raw == null) {
                    continue;
                }
                String id = raw.trim();
                if (!id.isEmpty()) {
                    normalized.add(id);
                }
            }
        }
        int added = trackerRepository.addAll(normalized);
        return new AddResult(added, normalized.size() - added);
    }

    /**
     * All trackers in the pool, each decorated with every team and game holding
     * it. Only non-completed (PLANNED/ACTIVE) games count: a completed game's
     * trackers are back at the desk and shown as unassigned. A tracker prepared
     * for the next game while the current one runs shows both assignments.
     */
    public List<TrackerWithAssignments> listTrackers() {
        Map<String, List<Assignment>> assignmentsByTrackerId = new HashMap<>();
        for (Game.GameStatus status : List.of(Game.GameStatus.PLANNED, Game.GameStatus.ACTIVE)) {
            for (Game game : gameRepository.findByStatus(status)) {
                for (Team team : teamRepository.findByGameId(game.getId())) {
                    String trackerId = team.getTrackerId();
                    if (trackerId != null && !trackerId.isBlank()) {
                        assignmentsByTrackerId
                                .computeIfAbsent(trackerId.trim(), id -> new ArrayList<>())
                                .add(new Assignment(team.getName(), game.getName()));
                    }
                }
            }
        }

        List<TrackerWithAssignments> result = new ArrayList<>();
        for (Tracker tracker : trackerRepository.findAll()) {
            result.add(new TrackerWithAssignments(
                    tracker,
                    assignmentsByTrackerId.getOrDefault(tracker.getTrackerId(), List.of())));
        }
        return result;
    }

    /** @return the updated tracker, or empty if the ID is not in the pool */
    @Transactional
    public Optional<Tracker> setEnabled(String trackerId, boolean enabled) {
        return trackerRepository.setEnabled(trackerId, enabled);
    }
}
