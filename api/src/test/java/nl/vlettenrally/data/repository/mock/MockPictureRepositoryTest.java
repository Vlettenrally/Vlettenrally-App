package nl.vlettenrally.data.repository.mock;

import nl.vlettenrally.data.domain.Picture;
import nl.vlettenrally.data.enums.PictureStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Unit tests for the in-memory query logic in {@link MockPictureRepository} — the
 * implementation the dev/test profiles use. Mirrors the JPA queries (cap count excludes
 * REJECTED, moderation queue is uploaded+PENDING, approved-by-game filter).
 */
class MockPictureRepositoryTest {

    private MockPictureRepository repository;
    private UUID teamA;
    private UUID teamB;
    private UUID gameId;

    @BeforeEach
    void setUp() {
        // Constructing directly skips the @PostConstruct seed, giving a clean slate.
        repository = new MockPictureRepository();
        teamA = UUID.randomUUID();
        teamB = UUID.randomUUID();
        gameId = UUID.randomUUID();
    }

    @Test
    void countActiveByTeamId_excludesRejected_includesNotYetUploaded() {
        save(teamA, PictureStatus.APPROVED, true);
        save(teamA, PictureStatus.PENDING, true);
        save(teamA, PictureStatus.PENDING, false); // reserved, not uploaded - still a slot
        save(teamA, PictureStatus.REJECTED, true);  // freed slot
        save(teamB, PictureStatus.PENDING, true);   // other team

        assertEquals(3, repository.countActiveByTeamId(teamA));
        assertEquals(1, repository.countActiveByTeamId(teamB));
    }

    @Test
    void findReadyForModeration_onlyUploadedPending() {
        save(teamA, PictureStatus.PENDING, true);   // ready
        save(teamA, PictureStatus.PENDING, false);  // not uploaded -> not ready
        save(teamA, PictureStatus.APPROVED, true);  // already moderated
        save(teamB, PictureStatus.REJECTED, true);

        List<Picture> ready = repository.findReadyForModeration();

        assertEquals(1, ready.size());
        assertEquals(PictureStatus.PENDING, ready.getFirst().getStatus());
        assertTrue(ready.getFirst().isUploaded());
    }

    @Test
    void findByStatus_filtersByStatus() {
        save(teamA, PictureStatus.APPROVED, true);
        save(teamB, PictureStatus.APPROVED, true);
        save(teamA, PictureStatus.REJECTED, true);

        assertEquals(2, repository.findByStatus(PictureStatus.APPROVED).size());
        assertEquals(1, repository.findByStatus(PictureStatus.REJECTED).size());
        assertTrue(repository.findByStatus(PictureStatus.PENDING).isEmpty());
    }

    @Test
    void findReadyForModerationByGameId_onlyUploadedPendingInGame() {
        UUID otherGame = UUID.randomUUID();
        save(teamA, PictureStatus.PENDING, true);          // ready, in game
        save(teamA, PictureStatus.PENDING, false);         // not yet uploaded
        Picture inOtherGame = save(teamA, PictureStatus.PENDING, true);
        inOtherGame.setGameId(otherGame);
        repository.save(inOtherGame);

        List<Picture> result = repository.findReadyForModerationByGameId(gameId);

        assertEquals(1, result.size());
        assertEquals(gameId, result.getFirst().getGameId());
    }

    @Test
    void findByStatusAndGameId_filtersByStatusAndGame() {
        UUID otherGame = UUID.randomUUID();
        save(teamA, PictureStatus.APPROVED, true);         // matches
        save(teamA, PictureStatus.PENDING, true);          // wrong status
        Picture wrongGame = save(teamB, PictureStatus.APPROVED, true);
        wrongGame.setGameId(otherGame);
        repository.save(wrongGame);

        List<Picture> result = repository.findByStatusAndGameId(PictureStatus.APPROVED, gameId);

        assertEquals(1, result.size());
        assertEquals(PictureStatus.APPROVED, result.getFirst().getStatus());
        assertEquals(gameId, result.getFirst().getGameId());
    }

    @Test
    void findAllByGameId_filtersByGame() {
        UUID otherGame = UUID.randomUUID();
        save(teamA, PictureStatus.APPROVED, true);
        save(teamB, PictureStatus.PENDING, true);
        Picture wrongGame = save(teamA, PictureStatus.REJECTED, true);
        wrongGame.setGameId(otherGame);
        repository.save(wrongGame);

        List<Picture> result = repository.findAllByGameId(gameId);

        assertEquals(2, result.size());
        assertTrue(result.stream().allMatch(p -> gameId.equals(p.getGameId())));
    }

    @Test
    void findApprovedByGameId_filtersByGameAndStatus() {
        UUID otherGame = UUID.randomUUID();
        Picture inGame = save(teamA, PictureStatus.APPROVED, true);
        save(teamA, PictureStatus.PENDING, true); // not approved
        Picture other = save(teamB, PictureStatus.APPROVED, true);
        other.setGameId(otherGame);
        repository.save(other);

        List<Picture> approved = repository.findApprovedByGameId(gameId);

        assertEquals(1, approved.size());
        assertEquals(inGame.getId(), approved.getFirst().getId());
    }

    @Test
    void findRecentApprovedByGameId_capsToLimit_newestFirst() {
        // Five approved, staggered upload times so ordering is deterministic.
        for (int i = 0; i < 5; i++) {
            Picture p = save(teamA, PictureStatus.APPROVED, true);
            p.setUploadedAt(Instant.now().plusSeconds(i));
            repository.save(p);
        }

        List<Picture> recent = repository.findRecentApprovedByGameId(gameId, 3);

        assertEquals(3, recent.size());
        // Newest first: each element is newer than the next.
        assertTrue(recent.get(0).getUploadedAt().isAfter(recent.get(1).getUploadedAt()));
        assertTrue(recent.get(1).getUploadedAt().isAfter(recent.get(2).getUploadedAt()));
    }

    @Test
    void findByTeamId_returnsOnlyThatTeam() {
        save(teamA, PictureStatus.APPROVED, true);
        save(teamA, PictureStatus.PENDING, true);
        save(teamB, PictureStatus.APPROVED, true);

        assertEquals(2, repository.findByTeamId(teamA).size());
        assertEquals(1, repository.findByTeamId(teamB).size());
    }

    @Test
    void delete_removesRow() {
        Picture p = save(teamA, PictureStatus.PENDING, true);
        repository.delete(p.getId());
        assertTrue(repository.findById(p.getId()).isEmpty());
    }

    private Picture save(UUID teamId, PictureStatus status, boolean uploaded) {
        Picture p = new Picture();
        p.setTeamId(teamId);
        p.setGameId(gameId);
        p.setStatus(status);
        p.setContentType("image/jpeg");
        p.setObjectKey("teams/" + teamId + "/" + p.getId());
        if (uploaded) {
            p.setUploadedAt(Instant.now());
            p.setSizeBytes(512_000L);
        }
        return repository.save(p);
    }
}
