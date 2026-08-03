package nl.vlettenrally.data.repository;

import nl.vlettenrally.data.domain.GamePausePeriod;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Repository for a game's emergency-stop periods. Reads must come from the primary
 * datasource: these windows gate distance accrual on the ingest path, and a stale
 * replica that still showed a closed pause as open would drop real distance.
 */
public interface GamePausePeriodRepository {

    /** All periods for the game, ordered by {@code pausedAt} ascending. */
    List<GamePausePeriod> findByGameId(UUID gameId);

    /** The period the game is currently frozen by, if any ({@code resumedAt} null). */
    Optional<GamePausePeriod> findOpenByGameId(UUID gameId);

    /**
     * Insert a new period, or persist {@code resumedAt} on an existing one. Those are the
     * only two writes a period ever sees.
     */
    GamePausePeriod save(GamePausePeriod period);
}
