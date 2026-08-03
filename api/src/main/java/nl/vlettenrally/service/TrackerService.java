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
     * Result of a bulk delete: how many trackers were removed, how many IDs were
     * not in the pool, and the teams that lost their tracker as a result.
     */
    public record DeleteResult(int deleted, int notFound, List<String> unassignedTeams) {}

    /** A team holding a tracker in a non-completed game. */
    private record Holder(Team team, Game game, String trackerId) {}

    /**
     * Add a pasted batch of tracker IDs to the global pool. IDs are trimmed and
     * de-duplicated; ones already in the pool are counted as duplicates.
     */
    @Transactional
    public AddResult addTrackers(List<String> rawTrackerIds) {
        LinkedHashSet<String> normalized = normalize(rawTrackerIds);
        int added = trackerRepository.addAll(normalized);
        return new AddResult(added, normalized.size() - added);
    }

    /**
     * Remove a batch of tracker IDs from the global pool. IDs are trimmed and
     * de-duplicated; ones that are not in the pool are counted as not found.
     * <p>
     * {@code teams.tracker_id} has no foreign key to the pool, so the database
     * would happily leave a team pointing at a tracker that no longer exists —
     * which then fails pool validation on the next assignment. Teams holding a
     * deleted tracker are therefore unassigned first, in the same transaction.
     * Only PLANNED/ACTIVE games are touched: a completed game's tracker IDs are
     * a historical record of which tracker a boat carried and stay as they are.
     */
    @Transactional
    public DeleteResult deleteTrackers(List<String> rawTrackerIds) {
        LinkedHashSet<String> ids = normalize(rawTrackerIds);
        if (ids.isEmpty()) {
            return new DeleteResult(0, 0, List.of());
        }

        List<String> unassignedTeams = new ArrayList<>();
        for (Holder holder : currentHolders()) {
            if (!ids.contains(holder.trackerId())) {
                continue;
            }
            holder.team().setTrackerId(null);
            holder.team().setTrackerAssignedAt(null);
            teamRepository.save(holder.team());
            unassignedTeams.add(holder.trackerId() + " -> " + holder.team().getName()
                    + " (" + holder.game().getName() + ")");
        }

        int deleted = trackerRepository.deleteAll(ids);
        return new DeleteResult(deleted, ids.size() - deleted, unassignedTeams);
    }

    /**
     * All trackers in the pool, each decorated with every team and game holding
     * it. Only non-completed (PLANNED/ACTIVE) games count: a completed game's
     * trackers are back at the desk and shown as unassigned. A tracker prepared
     * for the next game while the current one runs shows both assignments.
     */
    public List<TrackerWithAssignments> listTrackers() {
        Map<String, List<Assignment>> assignmentsByTrackerId = new HashMap<>();
        for (Holder holder : currentHolders()) {
            assignmentsByTrackerId
                    .computeIfAbsent(holder.trackerId(), id -> new ArrayList<>())
                    .add(new Assignment(holder.team().getName(), holder.game().getName()));
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

    /**
     * Every team currently holding a tracker, across PLANNED, ACTIVE and PAUSED games.
     * A completed game's trackers are back at the desk and count as unassigned; a paused
     * game's are still very much out on the water.
     */
    private List<Holder> currentHolders() {
        List<Holder> holders = new ArrayList<>();
        for (Game.GameStatus status : List.of(
                Game.GameStatus.PLANNED, Game.GameStatus.ACTIVE, Game.GameStatus.PAUSED)) {
            for (Game game : gameRepository.findByStatus(status)) {
                for (Team team : teamRepository.findByGameId(game.getId())) {
                    String trackerId = team.getTrackerId();
                    if (trackerId != null && !trackerId.isBlank()) {
                        holders.add(new Holder(team, game, trackerId.trim()));
                    }
                }
            }
        }
        return holders;
    }

    /** Trim, drop blanks and de-duplicate a pasted or posted batch of tracker IDs. */
    private LinkedHashSet<String> normalize(List<String> rawTrackerIds) {
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
        return normalized;
    }
}
