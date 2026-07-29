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
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

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

        // A few more approved pictures from both teams so the players' photowall is populated.
        put(picture("cccc0005-0000-0000-0000-000000000005",
            MockTeamRepository.TEAM_WINDKRACHT_ID, MockGameRepository.GAME_2026_ID,
            PictureStatus.APPROVED, now.minus(75, ChronoUnit.MINUTES), now.minus(73, ChronoUnit.MINUTES), now.minus(40, ChronoUnit.MINUTES)));
        put(picture("cccc0006-0000-0000-0000-000000000006",
            MockTeamRepository.TEAM_WATERVLOOIEN_ID, MockGameRepository.GAME_2026_ID,
            PictureStatus.APPROVED, now.minus(50, ChronoUnit.MINUTES), now.minus(48, ChronoUnit.MINUTES), now.minus(20, ChronoUnit.MINUTES)));
        put(picture("cccc0007-0000-0000-0000-000000000007",
            MockTeamRepository.TEAM_WINDKRACHT_ID, MockGameRepository.GAME_2026_ID,
            PictureStatus.APPROVED, now.minus(30, ChronoUnit.MINUTES), now.minus(28, ChronoUnit.MINUTES), now.minus(10, ChronoUnit.MINUTES)));

        // Bulk filler so the photowall and the approved tab page past the first batch of 24 in dev.
        // These are organisation (admin) uploads with no team: attributing them to a mock team
        // would push it over max-per-team and break the team upload flow in dev. Ids stay stable
        // (cccc01xx) so a dev session can be reloaded without them shifting.
        for (int i = 0; i < 40; i++) {
            Instant created = now.minus(200L + i * 3L, ChronoUnit.MINUTES);
            put(adminPicture(String.format("cccc01%02d-0000-0000-0000-0000000001%02d", i, i),
                MockGameRepository.GAME_2026_ID, created));
        }

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

    /** An organisation upload: no team, auto-approved, keyed under the game's admin prefix. */
    private Picture adminPicture(String id, UUID gameId, Instant createdAt) {
        UUID pictureId = UUID.fromString(id);
        Picture p = new Picture(pictureId, null, gameId,
            "games/" + gameId + "/admin/" + pictureId, PictureStatus.APPROVED, "image/jpeg", createdAt);
        p.setUploadedAt(createdAt.plus(2, ChronoUnit.MINUTES));
        p.setSizeBytes(512_000L);
        p.setModeratedAt(createdAt.plus(2, ChronoUnit.MINUTES));
        return p;
    }

    @Override
    public Optional<Picture> findById(UUID id) {
        return Optional.ofNullable(pictures.get(id));
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
    public List<Picture> findApprovedByGameId(UUID gameId) {
        return pictures.values().stream()
                .filter(p -> gameId.equals(p.getGameId()) && p.getStatus() == PictureStatus.APPROVED)
                .sorted(Comparator.comparing(Picture::getUploadedAt).reversed())
                .collect(Collectors.toList());
    }

    @Override
    public List<Picture> findReadyForModerationPage(UUID gameId, Instant cursorAt, UUID cursorId, int limit) {
        return page(
                pictures.values().stream()
                        .filter(p -> gameId == null || gameId.equals(p.getGameId()))
                        .filter(p -> p.getStatus() == PictureStatus.PENDING && p.getUploadedAt() != null),
                Picture::getUploadedAt, true, cursorAt, cursorId, limit);
    }

    @Override
    public List<Picture> findApprovedPage(UUID gameId, Instant cursorAt, UUID cursorId, int limit) {
        return page(
                pictures.values().stream()
                        .filter(p -> gameId == null || gameId.equals(p.getGameId()))
                        .filter(p -> p.getStatus() == PictureStatus.APPROVED),
                Picture::getUploadedAt, false, cursorAt, cursorId, limit);
    }

    @Override
    public List<Picture> findPage(PictureStatus status, UUID gameId, Instant cursorAt, UUID cursorId, int limit) {
        return page(
                pictures.values().stream()
                        .filter(p -> gameId == null || gameId.equals(p.getGameId()))
                        .filter(p -> status == null || p.getStatus() == status),
                Picture::getCreatedAt, false, cursorAt, cursorId, limit);
    }

    /**
     * Keyset page over an already-filtered stream, matching the JPA ordering exactly:
     * sort by (key, id) in the requested direction and keep only rows strictly past the cursor.
     */
    private List<Picture> page(Stream<Picture> filtered,
                               Function<Picture, Instant> sortKey,
                               boolean ascending, Instant cursorAt, UUID cursorId, int limit) {
        Comparator<Picture> order = Comparator.comparing(sortKey).thenComparing(Picture::getId);
        if (!ascending) {
            order = order.reversed();
        }
        Comparator<Picture> cursorOrder = order;
        return filtered
                .filter(p -> {
                    if (cursorAt == null) {
                        return true;
                    }
                    int cmp = sortKey.apply(p).compareTo(cursorAt);
                    if (cmp != 0) {
                        return ascending ? cmp > 0 : cmp < 0;
                    }
                    if (cursorId == null) {
                        return false;
                    }
                    int byId = p.getId().compareTo(cursorId);
                    return ascending ? byId > 0 : byId < 0;
                })
                .sorted(cursorOrder)
                .limit(limit)
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
    public long countModeratedBy(UUID adminId) {
        return pictures.values().stream()
                .filter(p -> adminId.equals(p.getModeratedBy()))
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
