package nl.vlettenrally.data.repository.mock;

import io.quarkus.arc.profile.IfBuildProfile;
import io.quarkus.logging.Log;
import jakarta.annotation.PostConstruct;
import jakarta.enterprise.context.ApplicationScoped;
import nl.vlettenrally.data.domain.Picture;
import nl.vlettenrally.data.enums.PictureStatus;
import nl.vlettenrally.data.repository.PictureRepository;

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
 * Mock implementation of PictureRepository with prefilled data.
 * Only active in dev and test profiles - replaced by JpaPictureRepository in prod.
 */
@IfBuildProfile(anyOf = {"dev", "test"})
@ApplicationScoped
public class MockPictureRepository implements PictureRepository {

    private final Map<UUID, Picture> pictures = new ConcurrentHashMap<>();

    @PostConstruct
    void init() {
        initializeData();
    }

    public void initializeData() {
        pictures.clear();
        Instant now = Instant.now();

        // Watervlooien: one approved, one uploaded-and-pending
        put(picture("cccc0001-0000-0000-0000-000000000001",
            MockTeamRepository.TEAM_WATERVLOOIEN_ID, MockGameRepository.GAME_2026_ID,
            PictureStatus.APPROVED, now.minus(120, ChronoUnit.MINUTES), now.minus(118, ChronoUnit.MINUTES), now.minus(60, ChronoUnit.MINUTES)));
        put(picture("cccc0002-0000-0000-0000-000000000002",
            MockTeamRepository.TEAM_WATERVLOOIEN_ID, MockGameRepository.GAME_2026_ID,
            PictureStatus.PENDING, now.minus(10, ChronoUnit.MINUTES), now.minus(9, ChronoUnit.MINUTES), null));

        // Windkracht: one rejected (still has its object), one pending
        put(picture("cccc0003-0000-0000-0000-000000000003",
            MockTeamRepository.TEAM_WINDKRACHT_ID, MockGameRepository.GAME_2026_ID,
            PictureStatus.REJECTED, now.minus(90, ChronoUnit.MINUTES), now.minus(88, ChronoUnit.MINUTES), now.minus(80, ChronoUnit.MINUTES)));
        put(picture("cccc0004-0000-0000-0000-000000000004",
            MockTeamRepository.TEAM_WINDKRACHT_ID, MockGameRepository.GAME_2026_ID,
            PictureStatus.PENDING, now.minus(5, ChronoUnit.MINUTES), now.minus(4, ChronoUnit.MINUTES), null));

        Log.info("🎭 MockPictureRepository initialized with " + pictures.size() + " pictures");
    }

    private void put(Picture p) {
        pictures.put(p.getId(), p);
    }

    private Picture picture(String id, UUID teamId, UUID gameId, PictureStatus status,
                            Instant createdAt, Instant uploadedAt, Instant moderatedAt) {
        UUID pictureId = UUID.fromString(id);
        Picture p = new Picture(pictureId, teamId, gameId,
            "games/" + gameId + "/teams/" + teamId + "/" + pictureId, status, "image/jpeg", createdAt);
        p.setUploadedAt(uploadedAt);
        p.setSizeBytes(uploadedAt != null ? 512_000L : null);
        p.setModeratedAt(moderatedAt);
        return p;
    }

    @Override
    public Optional<Picture> findById(UUID id) {
        return Optional.ofNullable(pictures.get(id));
    }

    @Override
    public List<Picture> findAll() {
        return pictures.values().stream()
                .sorted(Comparator.comparing(Picture::getCreatedAt).reversed())
                .collect(Collectors.toList());
    }

    @Override
    public List<Picture> findByTeamId(UUID teamId) {
        return pictures.values().stream()
                .filter(p -> teamId.equals(p.getTeamId()))
                .sorted(Comparator.comparing(Picture::getCreatedAt).reversed())
                .collect(Collectors.toList());
    }

    @Override
    public List<Picture> findByStatus(PictureStatus status) {
        return pictures.values().stream()
                .filter(p -> p.getStatus() == status)
                .sorted(Comparator.comparing(Picture::getCreatedAt).reversed())
                .collect(Collectors.toList());
    }

    @Override
    public List<Picture> findReadyForModeration() {
        return pictures.values().stream()
                .filter(p -> p.getStatus() == PictureStatus.PENDING && p.getUploadedAt() != null)
                .sorted(Comparator.comparing(Picture::getUploadedAt))
                .collect(Collectors.toList());
    }

    @Override
    public List<Picture> findApprovedByGameId(UUID gameId) {
        return pictures.values().stream()
                .filter(p -> gameId.equals(p.getGameId()) && p.getStatus() == PictureStatus.APPROVED)
                .sorted(Comparator.comparing(Picture::getUploadedAt).reversed())
                .collect(Collectors.toList());
    }

    @Override
    public long countActiveByTeamId(UUID teamId) {
        return pictures.values().stream()
                .filter(p -> teamId.equals(p.getTeamId()) && p.getStatus() != PictureStatus.REJECTED)
                .count();
    }

    @Override
    public long countAllByTeamId(UUID teamId) {
        return pictures.values().stream()
                .filter(p -> teamId.equals(p.getTeamId()))
                .count();
    }

    @Override
    public Picture save(Picture picture) {
        if (picture.getId() == null) {
            picture.setId(UUID.randomUUID());
        }
        pictures.put(picture.getId(), picture);
        return picture;
    }

    @Override
    public void delete(UUID id) {
        pictures.remove(id);
    }
}
