package nl.vlettenrally.data.repository.mock;

import io.quarkus.arc.profile.IfBuildProfile;
import jakarta.enterprise.context.ApplicationScoped;
import nl.vlettenrally.data.domain.TeamDistanceSegment;
import nl.vlettenrally.data.repository.TeamDistanceSegmentRepository;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Mock implementation of TeamDistanceSegmentRepository (starts empty).
 * Only active in dev and test profiles - replaced by the JPA impl in prod.
 * Semantics deliberately mirror the JPA implementation (same ordering, same
 * carryover-protected delete predicate); {@link #lockTeam} is a no-op.
 */
@IfBuildProfile(anyOf = {"dev", "test"})
@ApplicationScoped
public class MockTeamDistanceSegmentRepository implements TeamDistanceSegmentRepository {

    private final Map<UUID, List<TeamDistanceSegment>> segments = new ConcurrentHashMap<>();

    @Override
    public void lockTeam(UUID teamId) {
        // No-op: mock profile is single-instance and tests are single-threaded.
    }

    @Override
    public Optional<TeamDistanceSegment> findLastByTeamId(UUID teamId) {
        return teamSegments(teamId).stream()
                .max(Comparator.comparing(TeamDistanceSegment::getToTime));
    }

    @Override
    public double sumMetersByTeamId(UUID teamId) {
        return teamSegments(teamId).stream()
                .mapToDouble(TeamDistanceSegment::getMeters)
                .sum();
    }

    @Override
    public TeamDistanceSegment save(TeamDistanceSegment segment) {
        if (segment.getId() == null) {
            segment.setId(UUID.randomUUID());
        }
        segments.computeIfAbsent(segment.getTeamId(), k -> new ArrayList<>()).add(segment);
        return segment;
    }

    @Override
    public void deleteFromTime(UUID teamId, Instant boundaryInclusive) {
        // Never delete a carryover seed (fromTime null, meters > 0) — same rule as JPA.
        teamSegments(teamId).removeIf(s -> !s.getToTime().isBefore(boundaryInclusive)
                && (s.getFromTime() != null || s.getMeters() == 0));
    }

    @Override
    public List<TeamDistanceSegment> findByTeamId(UUID teamId) {
        return teamSegments(teamId).stream()
                .sorted(Comparator.comparing(TeamDistanceSegment::getToTime))
                .toList();
    }

    private List<TeamDistanceSegment> teamSegments(UUID teamId) {
        return segments.getOrDefault(teamId, new ArrayList<>());
    }
}
