package nl.vlettenrally.service;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import nl.vlettenrally.data.domain.Team;
import nl.vlettenrally.data.repository.TeamRepository;
import nl.vlettenrally.data.repository.TrackerPoolRepository;

import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Assigns a batch of physical GPS tracker IDs to a game's teams and exports the
 * resulting tracker -> boat mapping.
 *
 * <p>The external provider hands us a batch of interchangeable trackers; the exact
 * tracker-to-boat pairing does not matter, only that every boat ends up with a
 * unique tracker. The admin pastes the IDs and we hand them out automatically to
 * teams that don't have one yet. Re-pasting is safe: IDs already in use are
 * skipped, so it never double-assigns.
 *
 * <p>Every (re)assignment stamps {@code trackerAssignedAt}, which bounds location
 * backfill so a reassigned tracker's earlier movement stays with its previous
 * holder. Re-assigning a team's current tracker leaves the stamp untouched.
 */
@ApplicationScoped
public class TrackerAssignmentService {

    @Inject
    TeamRepository teamRepository;

    @Inject
    TrackerPoolRepository trackerPoolRepository;

    /**
     * Auto-distribute the pasted tracker IDs across teams without a tracker.
     *
     * @param gameId     the game whose teams receive trackers
     * @param trackerIds the pasted tracker IDs (raw; trimmed and de-duplicated here)
     * @return counts and detail lists describing what was assigned, left unassigned, and unused
     */
    @Transactional
    public TrackerAssignmentResult assignTrackers(UUID gameId, List<String> trackerIds) {
        List<Team> teams = teamRepository.findByGameId(gameId);

        // Tracker IDs already assigned to a team in this game must never be handed out again.
        Set<String> alreadyAssigned = teams.stream()
                .map(t -> normalize(t.getTrackerId()))
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());

        // Persist every pasted ID into the game's pool so that any not auto-assigned
        // here stay available to pick per team later. De-duped in the repository.
        LinkedHashSet<String> normalizedPasted = new LinkedHashSet<>();
        if (trackerIds != null) {
            for (String raw : trackerIds) {
                String id = normalize(raw);
                if (id != null) {
                    normalizedPasted.add(id);
                }
            }
        }
        trackerPoolRepository.addAll(gameId, normalizedPasted);

        // Candidate IDs: those just pasted that aren't already assigned to a team.
        LinkedHashSet<String> candidates = new LinkedHashSet<>();
        for (String id : normalizedPasted) {
            if (!alreadyAssigned.contains(id)) {
                candidates.add(id);
            }
        }

        List<Team> freeTeams = teams.stream()
                .filter(t -> normalize(t.getTrackerId()) == null)
                .toList();

        List<String> assignedPairs = new ArrayList<>();
        List<String> remainingTrackers = new ArrayList<>(candidates);

        int count = Math.min(freeTeams.size(), remainingTrackers.size());
        for (int i = 0; i < count; i++) {
            Team team = freeTeams.get(i);
            String trackerId = remainingTrackers.get(i);
            team.setTrackerId(trackerId);
            team.setTrackerAssignedAt(Instant.now());
            teamRepository.save(team);
            assignedPairs.add(trackerId + " -> " + boatLabel(team));
        }

        List<String> teamsWithoutTracker = freeTeams.stream()
                .skip(count)
                .map(this::boatLabel)
                .collect(Collectors.toList());
        List<String> unusedTrackers = remainingTrackers.stream()
                .skip(count)
                .collect(Collectors.toList());

        return new TrackerAssignmentResult(
                count,
                teamsWithoutTracker.size(),
                unusedTrackers.size(),
                assignedPairs,
                teamsWithoutTracker,
                unusedTrackers
        );
    }

    /**
     * Tracker IDs in the game's pool that are not currently assigned to any team,
     * i.e. the ones still pickable for a team. Sorted for a stable picker.
     */
    public List<String> availableTrackers(UUID gameId) {
        Set<String> assigned = teamRepository.findByGameId(gameId).stream()
                .map(t -> normalize(t.getTrackerId()))
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
        Set<String> available = new TreeSet<>(trackerPoolRepository.findTrackerIdsByGameId(gameId));
        available.removeAll(assigned);
        return new ArrayList<>(available);
    }

    /**
     * Assign a single tracker to a team, or clear it when {@code trackerId} is blank.
     * The tracker must already be in the game's pool and not assigned to another team.
     *
     * @throws IllegalArgumentException if the team is unknown or the tracker is not in the pool
     * @throws IllegalStateException    if the tracker is already assigned to a different team
     */
    @Transactional
    public Team assignToTeam(UUID teamId, String trackerId) {
        Team team = teamRepository.findById(teamId)
                .orElseThrow(() -> new IllegalArgumentException("Team not found"));

        String id = normalize(trackerId);
        if (id == null) {
            team.setTrackerId(null);
            team.setTrackerAssignedAt(null);
            return teamRepository.save(team);
        }

        List<Team> teams = teamRepository.findByGameId(team.getGameId());
        Optional<Team> holder = teams.stream()
                .filter(t -> !t.getId().equals(teamId) && id.equals(normalize(t.getTrackerId())))
                .findFirst();
        if (holder.isPresent()) {
            throw new IllegalStateException("Tracker " + id + " is already assigned to boat " + boatLabel(holder.get()));
        }
        if (!trackerPoolRepository.findTrackerIdsByGameId(team.getGameId()).contains(id)) {
            throw new IllegalArgumentException("Tracker " + id + " is not in this game's pool");
        }

        // Re-picking the tracker the team already holds must not re-stamp: the
        // assignment moment bounds location backfill, and shrinking it would lose
        // legitimately-owned track history.
        if (!id.equals(normalize(team.getTrackerId()))) {
            team.setTrackerAssignedAt(Instant.now());
        }
        team.setTrackerId(id);
        return teamRepository.save(team);
    }

    /**
     * CSV of the current tracker -> boat mapping for teams that have a tracker,
     * to send to the external provider. Columns: trackerId,boatNumber,scoutingGroup.
     */
    public String exportCsv(UUID gameId) {
        StringBuilder sb = new StringBuilder("trackerId,boatNumber,scoutingGroup\n");
        teamRepository.findByGameId(gameId).stream()
                .filter(t -> normalize(t.getTrackerId()) != null)
                .forEach(t -> sb
                        .append(csv(t.getTrackerId())).append(',')
                        .append(csv(t.getName())).append(',')
                        .append(csv(t.getDescription())).append('\n'));
        return sb.toString();
    }

    /** Trim a tracker ID; blank/null becomes {@code null} so it is ignored. */
    private String normalize(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    /** A team's boat number is its name; fall back to the id when unnamed. */
    private String boatLabel(Team team) {
        String name = team.getName();
        return name != null && !name.isBlank() ? name : team.getId().toString();
    }

    /** RFC-4180 escaping: quote fields containing comma, quote, or newline. */
    private String csv(String value) {
        if (value == null) {
            return "";
        }
        if (value.contains(",") || value.contains("\"") || value.contains("\n") || value.contains("\r")) {
            return '"' + value.replace("\"", "\"\"") + '"';
        }
        return value;
    }
}
