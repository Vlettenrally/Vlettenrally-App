package nl.vlettenrally.data.repository.mock;

import io.quarkus.arc.profile.IfBuildProfile;
import io.quarkus.logging.Log;
import jakarta.annotation.PostConstruct;
import jakarta.enterprise.context.ApplicationScoped;
import nl.vlettenrally.data.domain.Game;
import nl.vlettenrally.data.repository.GameRepository;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * Mock implementation of GameRepository with prefilled data.
 * Only active in dev and test profiles - replaced by PostgresGameRepository in prod.
 */
@IfBuildProfile(anyOf = {"dev", "test"})
@ApplicationScoped
public class MockGameRepository implements GameRepository {

    private final Map<UUID, Game> games = new ConcurrentHashMap<>();

    // Well-known UUIDs for consistent cross-reference
    public static final UUID GAME_2026_ID = UUID.fromString("a1b2c3d4-e5f6-7890-abcd-ef1234567890");
    public static final UUID GAME_SUMMER_ID = UUID.fromString("b2c3d4e5-f6a7-8901-bcde-f12345678901");

    @PostConstruct
    void init() {
        initializeData();
    }

    public void initializeData() {
        games.clear();
        
        Instant now = Instant.now();

        // Active game - Vlettenrally 2026
        Game game1 = new Game();
        game1.setId(GAME_2026_ID);
        game1.setName("Vlettenrally 2026");
        game1.setDescription("De jaarlijkse Vlettenrally");
        game1.setStartTime(now.minus(2, ChronoUnit.HOURS));
        game1.setEndTime(now.plus(4, ChronoUnit.HOURS));
        game1.setCheckInStartTime(now.minus(3, ChronoUnit.HOURS));
        game1.setStartSignalTime(now.minus(2, ChronoUnit.HOURS));
        game1.setStatus(Game.GameStatus.ACTIVE);
        games.put(game1.getId(), game1);

        // Planned game - Summer Edition
        Game game2 = new Game();
        game2.setId(GAME_SUMMER_ID);
        game2.setName("Vlettenrally Zomer Editie");
        game2.setDescription("Speciale zomereditie van de Vlettenrally");
        game2.setStartTime(now.plus(48, ChronoUnit.HOURS));
        game2.setEndTime(now.plus(54, ChronoUnit.HOURS));
        game2.setCheckInStartTime(now.plus(47, ChronoUnit.HOURS));
        game2.setStartSignalTime(now.plus(48, ChronoUnit.HOURS));
        game2.setStatus(Game.GameStatus.PLANNED);
        games.put(game2.getId(), game2);

        Log.info("🎭 MockGameRepository initialized with " + games.size() + " games");
    }

    @Override
    public Optional<Game> findById(UUID id) {
        return Optional.ofNullable(games.get(id));
    }

    @Override
    public List<Game> findAll() {
        return new ArrayList<>(games.values());
    }

    @Override
    public List<Game> findByStatus(Game.GameStatus status) {
        return games.values().stream()
                .filter(g -> g.getStatus() == status)
                .collect(Collectors.toList());
    }

    @Override
    public List<Game> findActiveGames() {
        return games.values().stream()
                .filter(g -> g.getStatus() == Game.GameStatus.ACTIVE || 
                            g.getStatus() == Game.GameStatus.PLANNED)
                .collect(Collectors.toList());
    }

    @Override
    public Game save(Game game) {
        if (game.getId() == null) {
            game.setId(UUID.randomUUID());
        }
        game.setUpdatedAt(Instant.now());
        games.put(game.getId(), game);
        return game;
    }

    @Override
    public void delete(UUID id) {
        games.remove(id);
    }

    @Override
    public int count() {
        return games.size();
    }
}
