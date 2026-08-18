package nl.vlettenrally.data.repository.mock;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Unit tests for {@link MockPointEntryRepository#sumCurrentAmountByTeams} — the dev/test-profile
 * stand-in for the grouped ledger SUM that {@code PointsTotalsCache.getAll} falls back to. It must
 * agree with the single-team sum it replaces, and honour the "absent, not zero" contract for teams
 * with no entries.
 */
class MockPointEntryRepositoryTest {

    private MockPointEntryRepository repository;

    @BeforeEach
    void setUp() {
        repository = new MockPointEntryRepository();
        repository.initializeData(); // @PostConstruct does not run outside the CDI container
    }

    @Test
    void sumCurrentAmountByTeams_agreesWithThePerTeamSum() {
        List<UUID> teamIds = List.of(
                MockTeamRepository.TEAM_WATERVLOOIEN_ID,
                MockTeamRepository.TEAM_WINDKRACHT_ID,
                MockTeamRepository.TEAM_GORILLAS_ID);

        Map<UUID, Integer> totals = repository.sumCurrentAmountByTeams(teamIds);

        assertEquals(teamIds.size(), totals.size());
        for (UUID teamId : teamIds) {
            assertEquals(repository.sumCurrentAmountByTeam(teamId), totals.get(teamId),
                    "grouped sum must match the per-team sum for " + teamId);
        }
    }

    @Test
    void sumCurrentAmountByTeams_returnsEmptyForNoTeams() {
        assertTrue(repository.sumCurrentAmountByTeams(List.of()).isEmpty());
        assertTrue(repository.sumCurrentAmountByTeams(null).isEmpty());
    }

    @Test
    void sumCurrentAmountByTeams_omitsTeamsWithoutEntries() {
        UUID withoutEntries = UUID.randomUUID();

        Map<UUID, Integer> totals = repository.sumCurrentAmountByTeams(
                List.of(MockTeamRepository.TEAM_WATERVLOOIEN_ID, withoutEntries));

        // Callers default a missing key to 0 rather than relying on an explicit zero here.
        assertFalse(totals.containsKey(withoutEntries));
        assertEquals(0, totals.getOrDefault(withoutEntries, 0));
        assertEquals(repository.sumCurrentAmountByTeam(MockTeamRepository.TEAM_WATERVLOOIEN_ID),
                totals.get(MockTeamRepository.TEAM_WATERVLOOIEN_ID));
    }

    @Test
    void sumCurrentAmountByTeams_ignoresTeamsNotAskedFor() {
        Map<UUID, Integer> totals = repository.sumCurrentAmountByTeams(
                List.of(MockTeamRepository.TEAM_WATERVLOOIEN_ID));

        assertEquals(Map.of(MockTeamRepository.TEAM_WATERVLOOIEN_ID,
                        repository.sumCurrentAmountByTeam(MockTeamRepository.TEAM_WATERVLOOIEN_ID)),
                totals);
    }
}
