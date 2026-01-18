package nl.vlettenrally.data.repository.mock;

import jakarta.annotation.PostConstruct;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Alternative;
import nl.vlettenrally.data.domain.Checkpoint;
import nl.vlettenrally.data.repository.CheckpointRepository;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * Mock implementation of CheckpointRepository with prefilled data.
 * Annotated as @Alternative - activate in application.properties for development.
 */
@Alternative
@ApplicationScoped
public class MockCheckpointRepository implements CheckpointRepository {

    private final Map<UUID, Checkpoint> checkpoints = new ConcurrentHashMap<>();

    // Well-known UUIDs for consistent cross-reference
    public static final UUID CHECKPOINT_START_ID = UUID.fromString("c0000001-0000-0000-0000-000000000001");
    public static final UUID CHECKPOINT_AMSTEL_ID = UUID.fromString("c0000002-0000-0000-0000-000000000002");
    public static final UUID CHECKPOINT_PRINSEN_ID = UUID.fromString("c0000003-0000-0000-0000-000000000003");
    public static final UUID CHECKPOINT_KEIZER_ID = UUID.fromString("c0000004-0000-0000-0000-000000000004");
    public static final UUID CHECKPOINT_FINISH_ID = UUID.fromString("c0000005-0000-0000-0000-000000000005");

    @PostConstruct
    void init() {
        initializeData();
    }

    public void initializeData() {
        checkpoints.clear();
        
        Instant now = Instant.now();

        // Checkpoint 1 - Start
        Checkpoint cp1 = new Checkpoint();
        cp1.setId(CHECKPOINT_START_ID);
        cp1.setGameId(MockGameRepository.GAME_2026_ID);
        cp1.setName("Start - Havenkade");
        cp1.setOpeningTime(now.minus(2, ChronoUnit.HOURS));
        cp1.setClosingTime(now.plus(4, ChronoUnit.HOURS));
        cp1.setPoints(0);
        cp1.setLatitude(52.3676);
        cp1.setLongitude(4.9041);
        checkpoints.put(cp1.getId(), cp1);

        // Checkpoint 2 - Amstel
        Checkpoint cp2 = new Checkpoint();
        cp2.setId(CHECKPOINT_AMSTEL_ID);
        cp2.setGameId(MockGameRepository.GAME_2026_ID);
        cp2.setName("Checkpoint Amstel");
        cp2.setOpeningTime(now.minus(90, ChronoUnit.MINUTES));
        cp2.setClosingTime(now.plus(3, ChronoUnit.HOURS));
        cp2.setPoints(100);
        cp2.setLatitude(52.3580);
        cp2.setLongitude(4.9000);
        checkpoints.put(cp2.getId(), cp2);

        // Checkpoint 3 - Prinsengracht
        Checkpoint cp3 = new Checkpoint();
        cp3.setId(CHECKPOINT_PRINSEN_ID);
        cp3.setGameId(MockGameRepository.GAME_2026_ID);
        cp3.setName("Checkpoint Prinsengracht");
        cp3.setOpeningTime(now.minus(1, ChronoUnit.HOURS));
        cp3.setClosingTime(now.plus(150, ChronoUnit.MINUTES));
        cp3.setPoints(150);
        cp3.setLatitude(52.3738);
        cp3.setLongitude(4.8820);
        checkpoints.put(cp3.getId(), cp3);

        // Checkpoint 4 - Keizersgracht
        Checkpoint cp4 = new Checkpoint();
        cp4.setId(CHECKPOINT_KEIZER_ID);
        cp4.setGameId(MockGameRepository.GAME_2026_ID);
        cp4.setName("Checkpoint Keizersgracht");
        cp4.setOpeningTime(now.minus(30, ChronoUnit.MINUTES));
        cp4.setClosingTime(now.plus(2, ChronoUnit.HOURS));
        cp4.setPoints(200);
        cp4.setLatitude(52.3702);
        cp4.setLongitude(4.8872);
        checkpoints.put(cp4.getId(), cp4);

        // Checkpoint 5 - Finish
        Checkpoint cp5 = new Checkpoint();
        cp5.setId(CHECKPOINT_FINISH_ID);
        cp5.setGameId(MockGameRepository.GAME_2026_ID);
        cp5.setName("Finish - Centraal Station");
        cp5.setOpeningTime(now.plus(1, ChronoUnit.HOURS));
        cp5.setClosingTime(now.plus(4, ChronoUnit.HOURS));
        cp5.setPoints(250);
        cp5.setLatitude(52.3791);
        cp5.setLongitude(4.9003);
        checkpoints.put(cp5.getId(), cp5);

        System.out.println("🎭 MockCheckpointRepository initialized with " + checkpoints.size() + " checkpoints");
    }

    @Override
    public Optional<Checkpoint> findById(UUID id) {
        return Optional.ofNullable(checkpoints.get(id));
    }

    @Override
    public List<Checkpoint> findAll() {
        return new ArrayList<>(checkpoints.values());
    }

    @Override
    public List<Checkpoint> findByGameId(UUID gameId) {
        return checkpoints.values().stream()
                .filter(cp -> gameId.equals(cp.getGameId()))
                .sorted(Comparator.comparing(Checkpoint::getOpeningTime))
                .collect(Collectors.toList());
    }

    @Override
    public Checkpoint save(Checkpoint checkpoint) {
        if (checkpoint.getId() == null) {
            checkpoint.setId(UUID.randomUUID());
        }
        checkpoint.setUpdatedAt(Instant.now());
        checkpoints.put(checkpoint.getId(), checkpoint);
        return checkpoint;
    }

    @Override
    public void delete(UUID id) {
        checkpoints.remove(id);
    }
}
