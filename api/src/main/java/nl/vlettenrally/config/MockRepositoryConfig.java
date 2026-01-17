package nl.vlettenrally.config;

import io.quarkus.arc.profile.IfBuildProfile;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Produces;
import nl.vlettenrally.repository.*;
import nl.vlettenrally.repository.mock.*;

/**
 * Configuration for mock repositories.
 * Active when running with the "dev" or "mock" profile.
 * 
 * Run with: ./mvnw quarkus:dev -Dquarkus.profile=mock
 * Or simply use dev mode (mock is default in dev)
 */
@ApplicationScoped
public class MockRepositoryConfig {

    @Produces
    @ApplicationScoped
    @IfBuildProfile(anyOf = {"dev", "mock"})
    public AdminRepository adminRepository() {
        MockAdminRepository repo = new MockAdminRepository();
        repo.initializeData();
        return repo;
    }

    @Produces
    @ApplicationScoped
    @IfBuildProfile(anyOf = {"dev", "mock"})
    public GameRepository gameRepository() {
        MockGameRepository repo = new MockGameRepository();
        repo.initializeData();
        return repo;
    }

    @Produces
    @ApplicationScoped
    @IfBuildProfile(anyOf = {"dev", "mock"})
    public TeamRepository teamRepository() {
        MockTeamRepository repo = new MockTeamRepository();
        repo.initializeData();
        return repo;
    }

    @Produces
    @ApplicationScoped
    @IfBuildProfile(anyOf = {"dev", "mock"})
    public CheckpointRepository checkpointRepository() {
        MockCheckpointRepository repo = new MockCheckpointRepository();
        repo.initializeData();
        return repo;
    }

    @Produces
    @ApplicationScoped
    @IfBuildProfile(anyOf = {"dev", "mock"})
    public AchievementRepository achievementRepository() {
        MockAchievementRepository repo = new MockAchievementRepository();
        repo.initializeData();
        return repo;
    }

    @Produces
    @ApplicationScoped
    @IfBuildProfile(anyOf = {"dev", "mock"})
    public MessageRepository messageRepository() {
        MockMessageRepository repo = new MockMessageRepository();
        repo.initializeData();
        return repo;
    }
}
