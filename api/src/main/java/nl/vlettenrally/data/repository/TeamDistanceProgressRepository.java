package nl.vlettenrally.data.repository;

import nl.vlettenrally.data.domain.TeamDistanceProgress;

import java.util.Optional;
import java.util.UUID;

/**
 * Repository interface for per-team travelled-distance accumulator state.
 * Reads must come from the primary datasource — a stale replica read would
 * re-process fixes and double-credit distance points.
 */
public interface TeamDistanceProgressRepository {

    Optional<TeamDistanceProgress> findByTeamId(UUID teamId);

    TeamDistanceProgress save(TeamDistanceProgress progress);
}
