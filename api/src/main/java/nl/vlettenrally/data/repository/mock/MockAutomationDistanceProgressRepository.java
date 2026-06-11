package nl.vlettenrally.data.repository.mock;

import io.quarkus.arc.profile.IfBuildProfile;
import jakarta.enterprise.context.ApplicationScoped;
import nl.vlettenrally.data.domain.AutomationDistanceProgress;
import nl.vlettenrally.data.repository.AutomationDistanceProgressRepository;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Mock implementation of AutomationDistanceProgressRepository (starts empty).
 * Only active in dev and test profiles - replaced by the JPA impl in prod.
 */
@IfBuildProfile(anyOf = {"dev", "test"})
@ApplicationScoped
public class MockAutomationDistanceProgressRepository implements AutomationDistanceProgressRepository {

    private record Key(UUID ruleId, UUID teamId) {}

    private final Map<Key, AutomationDistanceProgress> progress = new ConcurrentHashMap<>();

    @Override
    public Optional<AutomationDistanceProgress> findByRuleAndTeam(UUID ruleId, UUID teamId) {
        return Optional.ofNullable(progress.get(new Key(ruleId, teamId)));
    }

    @Override
    public AutomationDistanceProgress save(AutomationDistanceProgress p) {
        progress.put(new Key(p.getRuleId(), p.getTeamId()), p);
        return p;
    }
}
