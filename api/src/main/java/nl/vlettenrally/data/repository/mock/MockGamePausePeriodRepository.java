package nl.vlettenrally.data.repository.mock;

import io.quarkus.arc.profile.IfBuildProfile;
import jakarta.enterprise.context.ApplicationScoped;
import nl.vlettenrally.data.domain.GamePausePeriod;
import nl.vlettenrally.data.repository.GamePausePeriodRepository;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Mock implementation of GamePausePeriodRepository (starts empty — the seeded dev
 * games have never been paused). Only active in dev and test profiles.
 * Semantics deliberately mirror the JPA implementation, including the "at most one
 * open period per game" rule that the partial unique index enforces in Postgres.
 */
@IfBuildProfile(anyOf = {"dev", "test"})
@ApplicationScoped
public class MockGamePausePeriodRepository implements GamePausePeriodRepository {

    private final Map<UUID, List<GamePausePeriod>> periods = new ConcurrentHashMap<>();

    @Override
    public List<GamePausePeriod> findByGameId(UUID gameId) {
        return gamePeriods(gameId).stream()
                .sorted(Comparator.comparing(GamePausePeriod::getPausedAt))
                .toList();
    }

    @Override
    public Optional<GamePausePeriod> findOpenByGameId(UUID gameId) {
        return gamePeriods(gameId).stream()
                .filter(GamePausePeriod::isOpen)
                .findFirst();
    }

    @Override
    public GamePausePeriod save(GamePausePeriod period) {
        if (period.getId() == null) {
            period.setId(UUID.randomUUID());
        }
        List<GamePausePeriod> forGame =
                periods.computeIfAbsent(period.getGameId(), k -> new ArrayList<>());
        forGame.removeIf(p -> p.getId().equals(period.getId()));
        forGame.add(period);
        return period;
    }

    private List<GamePausePeriod> gamePeriods(UUID gameId) {
        return periods.getOrDefault(gameId, new ArrayList<>());
    }
}
