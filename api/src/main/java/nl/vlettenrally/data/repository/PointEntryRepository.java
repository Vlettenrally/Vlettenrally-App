package nl.vlettenrally.data.repository;

import nl.vlettenrally.data.domain.PointEntry;
import nl.vlettenrally.data.enums.PointEntryType;

import java.util.List;
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

    PointEntry save(PointEntry entry);

    void delete(UUID id);
}
