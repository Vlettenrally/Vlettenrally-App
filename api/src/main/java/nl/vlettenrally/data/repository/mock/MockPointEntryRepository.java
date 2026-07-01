package nl.vlettenrally.data.repository.mock;

import io.quarkus.arc.profile.IfBuildProfile;
import io.quarkus.logging.Log;
import jakarta.annotation.PostConstruct;
import jakarta.enterprise.context.ApplicationScoped;
import nl.vlettenrally.data.domain.PointEntry;
import nl.vlettenrally.data.enums.PointEntryType;
import nl.vlettenrally.data.repository.PointEntryRepository;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.Point;
import org.locationtech.jts.geom.PrecisionModel;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * Mock implementation of PointEntryRepository with prefilled data.
 * Only active in dev and test profiles - replaced by JpaPointEntryRepository in prod.
 * <p>
 * Seeded entries sum to the hardcoded team totals in MockTeamRepository
 * so totalPoints stays consistent with the ledger.
 */
@IfBuildProfile(anyOf = {"dev", "test"})
@ApplicationScoped
public class MockPointEntryRepository implements PointEntryRepository {

    private static final GeometryFactory GEOMETRY_FACTORY = new GeometryFactory(new PrecisionModel(), 4326);

    private final Map<UUID, PointEntry> entries = new ConcurrentHashMap<>();

    @PostConstruct
    void init() {
        initializeData();
    }

    public void initializeData() {
        entries.clear();

        Instant now = Instant.now();
        UUID game = MockGameRepository.GAME_2026_ID;
        UUID watervlooien = MockTeamRepository.TEAM_WATERVLOOIEN_ID;
        UUID windkracht = MockTeamRepository.TEAM_WINDKRACHT_ID;
        UUID gorillas = MockTeamRepository.TEAM_GORILLAS_ID;

        // Watervlooien — totalPoints = 350 → 100 + 150 + (originally 120, edited down to 100) = 350
        addVerification(watervlooien, game, 100, point(4.9000, 52.3580),
                "Checkpoint Amstel geverifieerd", now.minus(75, ChronoUnit.MINUTES));
        addVerification(watervlooien, game, 150, point(4.8820, 52.3738),
                "Checkpoint Prinsengracht geverifieerd", now.minus(45, ChronoUnit.MINUTES));
        PointEntry edited = addVerification(watervlooien, game, 120, point(4.8872, 52.3702),
                "Checkpoint Keizersgracht geverifieerd", now.minus(20, ChronoUnit.MINUTES));
        // Demonstrate the "Aftrek" column: 120 → 100 (deducted 20)
        edited.setCurrentAmount(100);
        edited.setEditedAt(now.minus(10, ChronoUnit.MINUTES));
        edited.setEditReason("Onreglementair gevaren tussen checkpoints");

        // Windkracht Tien — totalPoints = 300 → 100 + 200
        addVerification(windkracht, game, 100, point(4.9000, 52.3580),
                "Checkpoint Amstel geverifieerd", now.minus(70, ChronoUnit.MINUTES));
        addVerification(windkracht, game, 200, point(4.8872, 52.3702),
                "Checkpoint Keizersgracht geverifieerd", now.minus(30, ChronoUnit.MINUTES));

        // De Grachtengorrillas — totalPoints = 250 → 250 (one entry, no location to show that case)
        PointEntry manual = new PointEntry();
        manual.setTeamId(gorillas);
        manual.setGameId(game);
        manual.setEntryType(PointEntryType.MANUAL_ADJUSTMENT);
        manual.setOriginalAmount(250);
        manual.setCurrentAmount(250);
        manual.setDescription("Handmatige toekenning - testronde");
        manual.setCreatedAt(now.minus(2, ChronoUnit.HOURS));
        entries.put(manual.getId(), manual);

        // Kapitein Haak — totalPoints = 0, no entries

        Log.info("🎭 MockPointEntryRepository initialized with " + entries.size() + " point entries");
    }

    private PointEntry addVerification(UUID teamId, UUID gameId, int amount, Point location,
                                       String description, Instant createdAt) {
        PointEntry entry = new PointEntry();
        entry.setTeamId(teamId);
        entry.setGameId(gameId);
        entry.setEntryType(PointEntryType.CHECKPOINT_VERIFICATION);
        entry.setOriginalAmount(amount);
        entry.setCurrentAmount(amount);
        entry.setLocation(location);
        entry.setDescription(description);
        entry.setCreatedAt(createdAt);
        entries.put(entry.getId(), entry);
        return entry;
    }

    private static Point point(double longitude, double latitude) {
        return GEOMETRY_FACTORY.createPoint(new Coordinate(longitude, latitude));
    }

    @Override
    public Optional<PointEntry> findById(UUID id) {
        return Optional.ofNullable(entries.get(id));
    }

    @Override
    public List<PointEntry> findByGame(UUID gameId, UUID teamId, PointEntryType entryType, int page, int pageSize) {
        return entries.values().stream()
                .filter(e -> gameId.equals(e.getGameId()))
                .filter(e -> teamId == null || teamId.equals(e.getTeamId()))
                .filter(e -> entryType == null || entryType == e.getEntryType())
                .sorted(Comparator.comparing(PointEntry::getUpdatedAt).reversed())
                .skip((long) page * pageSize)
                .limit(pageSize)
                .collect(Collectors.toList());
    }

    @Override
    public long countByGame(UUID gameId, UUID teamId, PointEntryType entryType) {
        return entries.values().stream()
                .filter(e -> gameId.equals(e.getGameId()))
                .filter(e -> teamId == null || teamId.equals(e.getTeamId()))
                .filter(e -> entryType == null || entryType == e.getEntryType())
                .count();
    }

    @Override
    public int sumCurrentAmountByTeam(UUID teamId) {
        return entries.values().stream()
                .filter(e -> teamId.equals(e.getTeamId()))
                .mapToInt(PointEntry::getCurrentAmount)
                .sum();
    }

    @Override
    public PointEntry save(PointEntry entry) {
        if (entry.getId() == null) {
            entry.setId(UUID.randomUUID());
        }
        entries.put(entry.getId(), entry);
        return entry;
    }

    @Override
    public void delete(UUID id) {
        entries.remove(id);
    }

    @Override
    public void deleteByTeamAndGame(UUID teamId, UUID gameId) {
        entries.values().removeIf(e -> teamId.equals(e.getTeamId()) && gameId.equals(e.getGameId()));
    }
}
