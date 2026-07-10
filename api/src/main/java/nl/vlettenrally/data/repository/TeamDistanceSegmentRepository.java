package nl.vlettenrally.data.repository;

import nl.vlettenrally.data.domain.TeamDistanceSegment;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Repository for a team's distance-segment chain. Reads must come from the primary
 * datasource — a stale replica read would miss the true anchor (last segment) and
 * double-count distance.
 */
public interface TeamDistanceSegmentRepository {

    /**
     * Serialize all distance mutations for this team for the duration of the current
     * transaction. Must be called inside an active transaction, before reading the
     * chain that is about to be extended or rebuilt.
     */
    void lockTeam(UUID teamId);

    /** Latest segment by {@code toTime} — its endpoint is the anchor (last counted fix). */
    Optional<TeamDistanceSegment> findLastByTeamId(UUID teamId);

    /** The team's travelled distance: sum of all segment meters (0 when there are none). */
    double sumMetersByTeamId(UUID teamId);

    TeamDistanceSegment save(TeamDistanceSegment segment);

    /**
     * Delete every segment with {@code toTime >= boundary}, EXCEPT a carryover seed
     * ({@code fromTime} null and {@code meters > 0}) — its distance may predate the
     * location_data retention window and can never be re-derived from fixes.
     */
    void deleteFromTime(UUID teamId, Instant boundaryInclusive);

    /** All segments for the team, ordered by {@code toTime} ascending (tests/diagnostics). */
    List<TeamDistanceSegment> findByTeamId(UUID teamId);
}
