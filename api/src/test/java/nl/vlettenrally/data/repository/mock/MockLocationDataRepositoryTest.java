package nl.vlettenrally.data.repository.mock;

import nl.vlettenrally.data.domain.LocationData;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.PrecisionModel;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Unit tests for {@link MockLocationDataRepository#findLatestByGameId} — the dev/test-profile
 * stand-in for the LATERAL "latest fix per team" query. It must return exactly one row per team
 * with data, the newest one, and nothing for another game.
 */
class MockLocationDataRepositoryTest {

    private static final GeometryFactory GEOMETRY_FACTORY = new GeometryFactory(new PrecisionModel(), 4326);

    private MockLocationDataRepository repository;
    private UUID gameId;
    private UUID otherGameId;

    @BeforeEach
    void setUp() {
        repository = new MockLocationDataRepository();
        repository.findByGameId(MockGameRepository.GAME_2026_ID)
            .forEach(l -> repository.delete(l.getId())); // start from a clean slate
        gameId = UUID.randomUUID();
        otherGameId = UUID.randomUUID();
    }

    private LocationData store(UUID teamId, UUID game, Instant at) {
        LocationData loc = new LocationData();
        loc.setTeamId(teamId);
        loc.setGameId(game);
        loc.setLocation(GEOMETRY_FACTORY.createPoint(new Coordinate(4.9, 52.37)));
        loc.setLocationTime(at);
        return repository.save(loc);
    }

    @Test
    void findLatestByGameId_returnsTheNewestFixPerTeam() {
        Instant now = Instant.now();
        UUID first = UUID.randomUUID();
        UUID second = UUID.randomUUID();

        store(first, gameId, now.minus(30, ChronoUnit.MINUTES));
        LocationData newestOfFirst = store(first, gameId, now.minus(2, ChronoUnit.MINUTES));
        store(first, gameId, now.minus(10, ChronoUnit.MINUTES));
        LocationData onlyOfSecond = store(second, gameId, now.minus(20, ChronoUnit.MINUTES));

        Map<UUID, LocationData> byTeam = repository.findLatestByGameId(gameId).stream()
            .collect(Collectors.toMap(LocationData::getTeamId, Function.identity()));

        assertEquals(2, byTeam.size());
        assertEquals(newestOfFirst.getId(), byTeam.get(first).getId());
        assertEquals(onlyOfSecond.getId(), byTeam.get(second).getId());
    }

    @Test
    void findLatestByGameId_ignoresOtherGamesAndOmitsTeamsWithoutFixes() {
        Instant now = Instant.now();
        UUID teamInGame = UUID.randomUUID();
        UUID teamElsewhere = UUID.randomUUID();

        store(teamInGame, gameId, now);
        store(teamElsewhere, otherGameId, now);

        List<LocationData> result = repository.findLatestByGameId(gameId);

        assertEquals(1, result.size());
        assertEquals(teamInGame, result.getFirst().getTeamId());
    }

    @Test
    void findLatestByGameId_returnsEmpty_whenTheGameHasNoFixes() {
        assertTrue(repository.findLatestByGameId(UUID.randomUUID()).isEmpty());
    }
}
