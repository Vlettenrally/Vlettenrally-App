package nl.vlettenrally.service;

import io.quarkus.logging.Log;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import nl.vlettenrally.data.domain.Game;
import nl.vlettenrally.data.domain.PointEntry;
import nl.vlettenrally.data.domain.Team;
import nl.vlettenrally.data.enums.PointEntryType;
import nl.vlettenrally.data.repository.PointEntryRepository;
import nl.vlettenrally.data.repository.TeamRepository;

import java.time.Duration;
import java.util.Optional;

/**
 * Deducts the game's flat late-finish penalty from a team that was not in on time. There are two
 * moments that can trigger it, and whichever comes first wins:
 * <ul>
 *   <li>{@link #applyToUnfinishedTeams} — the game reaches COMPLETED while the team is still out.
 *       It is DNF from that instant, and it is charged then rather than being let off for never
 *       coming back. Called from both COMPLETED transitions (the scheduler and a manual admin
 *       status change).</li>
 *   <li>{@link #applyIfLate} — the team is marked finished after {@link Game#getEndTime()}, via
 *       the {@code FINISH_TEAM} automation action or the admin override on {@code PUT /teams/{id}}.
 *       This is the narrow window before the game flips to COMPLETED, and the path a team takes
 *       when it hands in its tracker late.</li>
 * </ul>
 * A team that goes DNF and then hands in its tracker therefore travels DNF → late, is charged once
 * at the game's end, and the later finish only moves the chip.
 * <p>
 * Teams that never checked in are skipped throughout: they did not start, which is a different
 * thing from a DNF, and the ranking leaves them out entirely.
 * <p>
 * {@code finishedAt} on the admin path is the moment the admin ticked the box, not the moment
 * the boat tied up, so a crew processed late at the desk can end up penalised. That is corrected
 * after the fact by editing or zeroing the LATE_FINISH_PENALTY entry in Points Management.
 * <p>
 * Un-finishing a team does <em>not</em> reverse the penalty: the ledger is an audit trail, and
 * the entry it leaves behind is what makes the deduction visible and correctable. The same entry
 * is the idempotency key, so neither an un-finish/re-finish cycle nor a game re-completed after
 * being reopened ever charges twice.
 */
@ApplicationScoped
public class LateFinishPenaltyService {

    @Inject
    GameService gameService;

    @Inject
    PointEntryRepository pointEntryRepository;

    @Inject
    PointsLedgerService pointsLedgerService;

    @Inject
    PointsTotalsCache pointsTotalsCache;

    @Inject
    TeamRepository teamRepository;

    /**
     * Apply the game's late-finish penalty to {@code team}, if it finished after the game's end
     * time and has not been penalised already.
     *
     * @return the ledger entry created, or empty when no penalty was due.
     */
    public Optional<PointEntry> applyIfLate(Team team) {
        if (team == null || !team.isFinished() || team.getFinishedAt() == null) {
            return Optional.empty();
        }

        Game game = gameService.findById(team.getGameId()).orElse(null);
        if (game == null || game.getEndTime() == null) {
            return Optional.empty();
        }
        if (!team.getFinishedAt().isAfter(game.getEndTime())) {
            return Optional.empty();
        }

        long minutesLate = Duration.between(game.getEndTime(), team.getFinishedAt()).toMinutes();
        return penalise(team, game, "Te laat gefinisht (" + minutesLate + " min na einde)");
    }

    /**
     * Charge every checked-in team that has not finished when the game reaches COMPLETED — the
     * instant they become DNF. Call it once per COMPLETED transition; teams already charged for a
     * late finish, and games with no penalty configured, cost nothing.
     *
     * @return how many teams were charged.
     */
    public int applyToUnfinishedTeams(Game game) {
        if (game == null || !hasPenalty(game)) {
            return 0;
        }
        int charged = 0;
        for (Team team : teamRepository.findByGameId(game.getId())) {
            if (team.isFinished() || !team.isCheckedIn()) {
                continue;
            }
            try {
                if (penalise(team, game, "Niet gefinisht bij einde spel").isPresent()) {
                    charged++;
                }
            } catch (Exception e) {
                // One team's ledger failing must not strand the rest, nor the transition itself.
                Log.errorf("DNF penalty failed for team %s: %s", team.getId(), e.getMessage());
            }
        }
        if (charged > 0) {
            Log.infof("DNF penalty applied to %d team(s) of game %s", charged, game.getId());
        }
        return charged;
    }

    private boolean hasPenalty(Game game) {
        Integer penalty = game.getLateFinishPenaltyPoints();
        return penalty != null && penalty > 0;
    }

    /**
     * Write the deduction, unless this team already carries one. The description records which of
     * the two moments got there first; it is not rewritten if the other follows later.
     */
    private Optional<PointEntry> penalise(Team team, Game game, String description) {
        if (!hasPenalty(game)) {
            return Optional.empty();
        }

        // Idempotency: one penalty per team per game, however often the finish or the game's
        // status is toggled.
        boolean alreadyPenalised = !pointEntryRepository
            .findByGame(game.getId(), team.getId(), PointEntryType.LATE_FINISH_PENALTY, 0, 1)
            .isEmpty();
        if (alreadyPenalised) {
            return Optional.empty();
        }

        // PointsLedgerService rejects an entry that would drop the total below zero. A team with
        // fewer points than the penalty must still be able to finish, so deduct what it has.
        int available = pointsTotalsCache.get(team.getId());
        int amount = -Math.min(game.getLateFinishPenaltyPoints(), available);
        if (amount == 0) {
            return Optional.empty();
        }

        PointEntry entry = pointsLedgerService.createEntry(
            team.getId(), game.getId(), PointEntryType.LATE_FINISH_PENALTY,
            amount, null, description, null, null);
        Log.infof("Late-finish penalty %d applied to team %s (%s)", amount, team.getId(), description);
        return Optional.of(entry);
    }
}
