package nl.vlettenrally.service;

import io.quarkus.logging.Log;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import nl.vlettenrally.data.domain.Game;
import nl.vlettenrally.data.domain.PointEntry;
import nl.vlettenrally.data.domain.Team;
import nl.vlettenrally.data.enums.PointEntryType;
import nl.vlettenrally.data.repository.PointEntryRepository;

import java.time.Duration;
import java.util.Optional;

/**
 * Awards the game's flat early-finish bonus to a team that finished in the closing stretch of
 * the game: at or after ({@code endTime - earlyFinishBonusMinutes}) and at or before
 * {@code endTime}. Finishing earlier than that window earns nothing — this rewards a strong
 * finish, not simply racing through as fast as possible.
 * <p>
 * Checked from the same two places {@link LateFinishPenaltyService#applyIfLate} is: the
 * {@code FINISH_TEAM} automation action and the admin override on {@code PUT /teams/{id}}.
 * Unlike the late-finish penalty, there is no game-completion sweep — a team that never
 * finished cannot have finished early, so there is nothing to charge retroactively.
 * <p>
 * Idempotent via the same mechanism as the penalty: one {@code EARLY_FINISH_BONUS} ledger entry
 * per team per game, however often the finish is toggled.
 * <p>
 * {@code finishedAt} on the admin path is the moment the admin ticked the box, not the moment
 * the boat tied up (see {@link LateFinishPenaltyService}). With a one-sided penalty that lag can
 * only push a team into being late; with this two-sided window it can just as easily grant or
 * deny the bonus for a crew that was actually inside or outside it. As with the penalty, that is
 * corrected after the fact by editing or removing the {@code EARLY_FINISH_BONUS} entry in Points
 * Management.
 */
@ApplicationScoped
public class EarlyFinishBonusService {

    @Inject
    GameService gameService;

    @Inject
    PointEntryRepository pointEntryRepository;

    @Inject
    PointsLedgerService pointsLedgerService;

    /**
     * Apply the game's early-finish bonus to {@code team}, if it finished within the qualifying
     * window and has not been awarded it already.
     *
     * @return the ledger entry created, or empty when no bonus was due.
     */
    public Optional<PointEntry> applyIfEarly(Team team) {
        if (team == null || !team.isFinished() || team.getFinishedAt() == null) {
            return Optional.empty();
        }

        Game game = gameService.findById(team.getGameId()).orElse(null);
        if (game == null || game.getEndTime() == null) {
            return Optional.empty();
        }
        if (!hasBonus(game)) {
            return Optional.empty();
        }

        var finishedAt = team.getFinishedAt();
        var endTime = game.getEndTime();
        var windowStart = endTime.minus(Duration.ofMinutes(game.getEarlyFinishBonusMinutes()));
        if (finishedAt.isBefore(windowStart) || finishedAt.isAfter(endTime)) {
            return Optional.empty();
        }

        long minutesEarly = Duration.between(finishedAt, endTime).toMinutes();
        return award(team, game, "Vroeg gefinisht (" + minutesEarly + " min voor einde)");
    }

    private boolean hasBonus(Game game) {
        Integer points = game.getEarlyFinishBonusPoints();
        Integer minutes = game.getEarlyFinishBonusMinutes();
        return points != null && points > 0 && minutes != null;
    }

    private Optional<PointEntry> award(Team team, Game game, String description) {
        // Idempotency: one bonus per team per game, however often the finish is toggled.
        boolean alreadyAwarded = !pointEntryRepository
            .findByGame(game.getId(), team.getId(), PointEntryType.EARLY_FINISH_BONUS, 0, 1)
            .isEmpty();
        if (alreadyAwarded) {
            return Optional.empty();
        }

        PointEntry entry = pointsLedgerService.createEntry(
            team.getId(), game.getId(), PointEntryType.EARLY_FINISH_BONUS,
            game.getEarlyFinishBonusPoints(), null, description, null, null);
        Log.infof("Early-finish bonus %d applied to team %s (%s)",
            game.getEarlyFinishBonusPoints(), team.getId(), description);
        return Optional.of(entry);
    }
}
