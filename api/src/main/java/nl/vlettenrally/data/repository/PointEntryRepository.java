package nl.vlettenrally.data.repository;

import nl.vlettenrally.data.domain.PointEntry;
import nl.vlettenrally.data.enums.PointEntryType;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Repository interface for PointEntry — the ledger of every change to a team's totalPoints.
 */
public interface PointEntryRepository {

    Optional<PointEntry> findById(UUID id);

    /**
     * List entries for a game, newest first. Optional filters by team and entry type.
     * Pagination is zero-based.
     */
    List<PointEntry> findByGame(UUID gameId, UUID teamId, PointEntryType entryType, int page, int pageSize);

    /**
     * Count entries matching the same filters as {@link #findByGame}.
     */
    long countByGame(UUID gameId, UUID teamId, PointEntryType entryType);

    /**
     * Sum of {@code currentAmount} across all entries for a team. Returns 0 for a team with no entries.
     */
    int sumCurrentAmountByTeam(UUID teamId);

    /**
     * Same sum as {@link #sumCurrentAmountByTeam}, for many teams in one query. Serializing a
     * list of teams needs every team's total at once; calling the single-team form per team is
     * one round trip per team.
     * <p>
     * Teams with no ledger entries are <em>absent</em> from the map rather than mapped to 0 —
     * callers should default a missing key to 0.
     */
    Map<UUID, Integer> sumCurrentAmountByTeams(Collection<UUID> teamIds);

    /**
     * Sum of {@code currentAmount} across all entries for a game. Optional filter by entry
     * type (null = all types). Returns 0 for a game with no matching entries.
     */
    int sumCurrentAmountByGame(UUID gameId, PointEntryType entryType);

    /**
     * Count entries an admin created or last edited. Used to decide whether that admin can be
     * deleted outright without silently dropping the ledger's authorship trail.
     */
    long countByCreatedByOrEditedBy(UUID adminId);

    PointEntry save(PointEntry entry);

    void delete(UUID id);

    void deleteByTeamAndGame(UUID teamId, UUID gameId);
}
