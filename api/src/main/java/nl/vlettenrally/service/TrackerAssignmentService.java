package nl.vlettenrally.service;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import nl.vlettenrally.data.domain.Team;
import nl.vlettenrally.data.domain.Tracker;
import nl.vlettenrally.data.repository.TeamRepository;
import nl.vlettenrally.data.repository.TrackerRepository;

import java.time.Instant;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Assigns physical GPS trackers from the global pool to a game's teams and
 * exports the resulting tracker -> boat mapping.
 *
 * <p>Trackers are interchangeable; the exact tracker-to-boat pairing does not
 * matter, only that every boat ends up with a unique tracker. Trackers are
 * handed out automatically to teams that don't have one yet. A tracker is
 * eligible when it is enabled in the pool and not held by another team in the
 * same game. Uniqueness is per game on purpose: the rally desk prepares the
 * next (planned) game while the current one is still running, so the same
 * tracker may be assigned in multiple games at once. Re-running is safe: it
 * never double-assigns within a game.
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
    TrackerRepository trackerRepository;

    /**
     * Auto-distribute free trackers from the global pool across teams without a tracker.
     *
     * @param gameId the game whose teams receive trackers
     * @return counts and detail lists describing what was assigned, left unassigned, and unused
     */
    @Transactional
    public TrackerAssignmentResult assignTrackers(UUID gameId) {
        List<Team> teams = teamRepository.findByGameId(gameId);

        // Enabled trackers not held by a team in THIS game; sorted so repeated
        // runs hand them out in a deterministic order.
        Set<String> candidates = new TreeSet<>(trackerRepository.findEnabledTrackerIds());
        candidates.removeAll(assignedInGame(teams));

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
     * Enabled trackers in the global pool that are not held by another team in
     * this game, i.e. the ones still pickable here. Sorted for a stable picker.
     */
    public List<String> availableTrackers(UUID gameId) {
        Set<String> available = new TreeSet<>(trackerRepository.findEnabledTrackerIds());
        available.removeAll(assignedInGame(teamRepository.findByGameId(gameId)));
        return new ArrayList<>(available);
    }

    /**
     * Assign a single tracker to a team, or clear it when {@code trackerId} is blank.
     * The tracker must be enabled in the global pool and not held by another team
     * in the same game (holding it in a different game is fine).
     *
     * @throws IllegalArgumentException if the team is unknown or the tracker is not an enabled pool member
     * @throws IllegalStateException    if the tracker is already assigned to a different team in this game
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

        Optional<Team> holder = teamRepository.findByGameId(team.getGameId()).stream()
                .filter(t -> !t.getId().equals(teamId) && id.equals(normalize(t.getTrackerId())))
                .findFirst();
        if (holder.isPresent()) {
            throw new IllegalStateException("Tracker " + id + " is already assigned to boat " + boatLabel(holder.get()));
        }
        Optional<Tracker> tracker = trackerRepository.findByTrackerId(id);
        if (tracker.isEmpty()) {
            throw new IllegalArgumentException("Tracker " + id + " is not in the tracker pool");
        }
        if (!tracker.get().isEnabled()) {
            throw new IllegalArgumentException("Tracker " + id + " is disabled");
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

    /** A team field that can be exported, with the column name it gets by default. */
    public enum ExportField {
        TRACKER_ID("trackerId", "trackerId", Team::getTrackerId),
        NAME("name", "boatNumber", Team::getName),
        DESCRIPTION("description", "scoutingGroup", Team::getDescription);

        private final String key;
        private final String defaultHeader;
        private final Function<Team, String> accessor;

        ExportField(String key, String defaultHeader, Function<Team, String> accessor) {
            this.key = key;
            this.defaultHeader = defaultHeader;
            this.accessor = accessor;
        }

        public String key() {
            return key;
        }

        public String defaultHeader() {
            return defaultHeader;
        }

        public static Optional<ExportField> from(String key) {
            return Arrays.stream(values())
                    .filter(f -> f.key.equals(key))
                    .findFirst();
        }
    }

    /** One CSV column: which team field, under which (renameable) column name. */
    public record ExportColumn(ExportField field, String header) {}

    /** The provider's historical format, used when the caller picks no columns. */
    public static final List<ExportColumn> DEFAULT_COLUMNS = Arrays.stream(ExportField.values())
            .map(f -> new ExportColumn(f, f.defaultHeader()))
            .toList();

    /**
     * CSV of the current tracker -> boat mapping for teams that have a tracker,
     * to send to the external provider. Columns: trackerId,boatNumber,scoutingGroup.
     */
    public String exportCsv(UUID gameId) {
        return exportCsv(gameId, DEFAULT_COLUMNS, false);
    }

    /**
     * CSV of the tracker -> boat mapping, shaped by the caller: which team fields
     * become columns, in which order, and under which column names.
     *
     * @param columns           the columns to emit, in order
     * @param includeUnassigned also emit teams that have no tracker yet
     */
    public String exportCsv(UUID gameId, List<ExportColumn> columns, boolean includeUnassigned) {
        StringBuilder sb = new StringBuilder();
        appendRow(sb, columns.stream().map(ExportColumn::header));
        teamRepository.findByGameId(gameId).stream()
                .filter(t -> includeUnassigned || normalize(t.getTrackerId()) != null)
                .forEach(t -> appendRow(sb, columns.stream().map(c -> c.field().accessor.apply(t))));
        return sb.toString();
    }

    /** Append one CSV line, escaping each cell. */
    private void appendRow(StringBuilder sb, Stream<String> cells) {
        sb.append(cells.map(this::csv).collect(Collectors.joining(","))).append('\n');
    }

    /** Tracker IDs held by the given (single game's) teams. */
    private Set<String> assignedInGame(List<Team> teams) {
        return teams.stream()
                .map(t -> normalize(t.getTrackerId()))
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
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
