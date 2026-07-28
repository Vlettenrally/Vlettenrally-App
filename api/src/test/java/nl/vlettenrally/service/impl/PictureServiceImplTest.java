package nl.vlettenrally.service.impl;

import nl.vlettenrally.data.domain.Game;
import nl.vlettenrally.data.domain.Picture;
import nl.vlettenrally.data.domain.Team;
import nl.vlettenrally.data.enums.PictureStatus;
import nl.vlettenrally.data.repository.GameRepository;
import nl.vlettenrally.data.repository.PictureRepository;
import nl.vlettenrally.data.repository.TeamRepository;
import nl.vlettenrally.service.AutomationEngine;
import nl.vlettenrally.service.PictureException;
import nl.vlettenrally.service.PictureService;
import nl.vlettenrally.service.storage.PictureStorageService;
import nl.vlettenrally.websockets.PhotoBroadcastBuffer;
import nl.vlettenrally.websockets.WebSocketPublisher;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link PictureServiceImpl}: the upload-reservation cap, content-type /
 * size validation, the confirm step's HeadObject checks, and the moderation transitions.
 */
@ExtendWith(MockitoExtension.class)
class PictureServiceImplTest {

    @Mock PictureRepository pictureRepository;
    @Mock TeamRepository teamRepository;
    @Mock GameRepository gameRepository;
    @Mock PictureStorageService storageService;
    @Mock WebSocketPublisher webSocketPublisher;
    @Mock PhotoBroadcastBuffer photoBroadcastBuffer;
    @Mock AutomationEngine automationEngine;

    @InjectMocks PictureServiceImpl service;

    private UUID teamId;
    private UUID gameId;
    private Team team;

    @BeforeEach
    void setUp() {
        // @ConfigProperty fields are package-private; set them directly for the unit test.
        service.maxPerTeam = 5;
        service.maxAttemptsPerTeam = 20;
        service.maxBytes = 10 * 1024 * 1024;
        service.allowedContentTypes = List.of("image/jpeg", "image/png", "image/webp");
        service.uploadUrlTtl = Duration.ofMinutes(15);
        service.viewUrlTtl = Duration.ofHours(1);
        service.photowallLimit = 60;

        teamId = UUID.randomUUID();
        gameId = UUID.randomUUID();
        team = new Team();
        team.setId(teamId);
        team.setGameId(gameId);
        team.setName("De Watervlooien");
        team.setDescription("Snelle en wendbare vlettenvaarders");
    }

    // ==================== requestUpload ====================

    @Test
    void requestUpload_reservesPendingPicture_andPresignsUrl() {
        when(teamRepository.findById(teamId)).thenReturn(Optional.of(team));
        when(gameRepository.findById(gameId)).thenReturn(Optional.of(gameWith(Game.GameStatus.ACTIVE)));
        when(pictureRepository.countActiveByTeamId(teamId)).thenReturn(2L);
        when(pictureRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(storageService.presignUpload(any(), eq("image/jpeg"), eq(Duration.ofMinutes(15))))
            .thenReturn("https://storj.example/upload");

        PictureService.UploadTicket ticket = service.requestUpload(teamId, "image/jpeg");

        Picture saved = ticket.picture();
        assertEquals(PictureStatus.PENDING, saved.getStatus());
        assertEquals(teamId, saved.getTeamId());
        assertEquals(gameId, saved.getGameId());
        assertEquals("image/jpeg", saved.getContentType());
        assertNull(saved.getUploadedAt(), "reservation must not be marked uploaded yet");
        assertEquals("games/" + gameId + "/teams/" + teamId + "/" + saved.getId(), saved.getObjectKey());
        assertEquals("https://storj.example/upload", ticket.uploadUrl());
        assertNotNull(ticket.expiresAt());
        // The row is persisted BEFORE the URL is issued (cap + single-use safety).
        verify(pictureRepository).save(any());
    }

    @Test
    void requestUpload_normalizesContentType() {
        when(teamRepository.findById(teamId)).thenReturn(Optional.of(team));
        when(gameRepository.findById(gameId)).thenReturn(Optional.of(gameWith(Game.GameStatus.ACTIVE)));
        when(pictureRepository.countActiveByTeamId(teamId)).thenReturn(0L);
        when(pictureRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(storageService.presignUpload(any(), eq("image/png"), any())).thenReturn("url");

        PictureService.UploadTicket ticket = service.requestUpload(teamId, "  IMAGE/PNG ");

        assertEquals("image/png", ticket.picture().getContentType());
    }

    @Test
    void requestUpload_rejectsDisallowedContentType_with415() {
        PictureException ex = assertThrows(PictureException.class,
            () -> service.requestUpload(teamId, "application/pdf"));
        assertEquals(415, ex.getStatusCode());
        verify(pictureRepository, never()).save(any());
        verify(storageService, never()).presignUpload(any(), any(), any());
    }

    @Test
    void requestUpload_rejectsWhenAtCap_with409() {
        when(teamRepository.findById(teamId)).thenReturn(Optional.of(team));
        when(gameRepository.findById(gameId)).thenReturn(Optional.of(gameWith(Game.GameStatus.ACTIVE)));
        when(pictureRepository.countActiveByTeamId(teamId)).thenReturn(5L);

        PictureException ex = assertThrows(PictureException.class,
            () -> service.requestUpload(teamId, "image/jpeg"));
        assertEquals(409, ex.getStatusCode());
        verify(pictureRepository, never()).save(any());
    }

    @Test
    void requestUpload_rejectsWhenGameNotActive_with409() {
        when(teamRepository.findById(teamId)).thenReturn(Optional.of(team));
        when(gameRepository.findById(gameId)).thenReturn(Optional.of(gameWith(Game.GameStatus.PLANNED)));

        PictureException ex = assertThrows(PictureException.class,
            () -> service.requestUpload(teamId, "image/jpeg"));
        assertEquals(409, ex.getStatusCode());
        verify(pictureRepository, never()).countActiveByTeamId(any());
        verify(pictureRepository, never()).save(any());
    }

    @Test
    void requestUpload_unknownGame_with404() {
        when(teamRepository.findById(teamId)).thenReturn(Optional.of(team));
        when(gameRepository.findById(gameId)).thenReturn(Optional.empty());

        PictureException ex = assertThrows(PictureException.class,
            () -> service.requestUpload(teamId, "image/jpeg"));
        assertEquals(404, ex.getStatusCode());
    }

    @Test
    void requestUpload_unknownTeam_with404() {
        when(teamRepository.findById(teamId)).thenReturn(Optional.empty());

        PictureException ex = assertThrows(PictureException.class,
            () -> service.requestUpload(teamId, "image/jpeg"));
        assertEquals(404, ex.getStatusCode());
    }

    // ==================== confirmUpload ====================

    @Test
    void confirmUpload_marksUploaded_andStoresSize() {
        Picture pending = reservation();
        when(pictureRepository.findById(pending.getId())).thenReturn(Optional.of(pending));
        when(storageService.head(pending.getObjectKey()))
            .thenReturn(Optional.of(new PictureStorageService.ObjectMetadata(123_456L, "image/jpeg")));
        when(pictureRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        Picture result = service.confirmUpload(teamId, pending.getId());

        assertTrue(result.isUploaded());
        assertEquals(123_456L, result.getSizeBytes());
        verify(storageService, never()).delete(any());
        // Admins get a live "new picture to moderate" event.
        verify(webSocketPublisher).sendToAdminTopic(eq(gameId), eq("pictures"), eq("PICTURE_PENDING"), any());
    }

    @Test
    void confirmUpload_missingObject_with409() {
        Picture pending = reservation();
        when(pictureRepository.findById(pending.getId())).thenReturn(Optional.of(pending));
        when(storageService.head(pending.getObjectKey())).thenReturn(Optional.empty());

        PictureException ex = assertThrows(PictureException.class,
            () -> service.confirmUpload(teamId, pending.getId()));
        assertEquals(409, ex.getStatusCode());
    }

    @Test
    void confirmUpload_oversizeObject_isDeleted_with422() {
        Picture pending = reservation();
        when(pictureRepository.findById(pending.getId())).thenReturn(Optional.of(pending));
        when(storageService.head(pending.getObjectKey()))
            .thenReturn(Optional.of(new PictureStorageService.ObjectMetadata(20L * 1024 * 1024, "image/jpeg")));

        PictureException ex = assertThrows(PictureException.class,
            () -> service.confirmUpload(teamId, pending.getId()));
        assertEquals(422, ex.getStatusCode());
        // Invalid upload: object and reservation are dropped so the slot frees up.
        verify(storageService).delete(pending.getObjectKey());
        verify(pictureRepository).delete(pending.getId());
    }

    @Test
    void confirmUpload_otherTeamsPicture_with404() {
        Picture pending = reservation();
        when(pictureRepository.findById(pending.getId())).thenReturn(Optional.of(pending));

        PictureException ex = assertThrows(PictureException.class,
            () -> service.confirmUpload(UUID.randomUUID(), pending.getId()));
        assertEquals(404, ex.getStatusCode());
        verify(storageService, never()).head(any());
    }

    // ==================== deleteOwnPicture ====================

    @Test
    void deleteOwnPicture_pending_deletesObjectAndRow() {
        Picture pending = reservation();
        when(pictureRepository.findById(pending.getId())).thenReturn(Optional.of(pending));

        service.deleteOwnPicture(teamId, pending.getId());

        verify(storageService).delete(pending.getObjectKey());
        verify(pictureRepository).delete(pending.getId());
    }

    @Test
    void deleteOwnPicture_nonPending_with409() {
        Picture approved = reservation();
        approved.setStatus(PictureStatus.APPROVED);
        when(pictureRepository.findById(approved.getId())).thenReturn(Optional.of(approved));

        PictureException ex = assertThrows(PictureException.class,
            () -> service.deleteOwnPicture(teamId, approved.getId()));
        assertEquals(409, ex.getStatusCode());
        verify(pictureRepository, never()).delete(any());
    }

    // ==================== approve / reject ====================

    @Test
    void approve_setsApprovedAndModerator() {
        UUID adminId = UUID.randomUUID();
        Picture uploaded = uploaded();
        when(pictureRepository.findById(uploaded.getId())).thenReturn(Optional.of(uploaded));
        when(pictureRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        Picture result = service.approve(uploaded.getId(), adminId);

        assertEquals(PictureStatus.APPROVED, result.getStatus());
        assertEquals(adminId, result.getModeratedBy());
        assertNotNull(result.getModeratedAt());
        // The owning team and the admin queues are notified live.
        verify(webSocketPublisher).sendToTeam(eq(gameId), eq(teamId), eq("PICTURE_MODERATED"), any());
        verify(webSocketPublisher).sendToAdminTopic(eq(gameId), eq("pictures"), eq("PICTURE_MODERATED"), any());
        // Approved pictures are queued for the batched photowall broadcast.
        verify(photoBroadcastBuffer).buffer(eq(gameId), any());
        // Approval is an automation trigger for the owning team.
        verify(automationEngine).onPictureApproved(gameId, teamId);
    }

    @Test
    void approve_alreadyApproved_doesNotRetriggerAutomations() {
        Picture uploaded = uploaded();
        uploaded.setStatus(PictureStatus.APPROVED);
        when(pictureRepository.findById(uploaded.getId())).thenReturn(Optional.of(uploaded));
        when(pictureRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        service.approve(uploaded.getId(), UUID.randomUUID());

        verifyNoInteractions(automationEngine);
    }

    @Test
    void approve_adminUpload_doesNotTriggerAutomations() {
        Picture uploaded = uploaded();
        uploaded.setTeamId(null); // admin uploads have no owning team to fire for
        when(pictureRepository.findById(uploaded.getId())).thenReturn(Optional.of(uploaded));
        when(pictureRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        service.approve(uploaded.getId(), UUID.randomUUID());

        verifyNoInteractions(automationEngine);
    }

    @Test
    void approve_notYetUploaded_with409() {
        Picture pending = reservation(); // uploadedAt == null
        when(pictureRepository.findById(pending.getId())).thenReturn(Optional.of(pending));

        PictureException ex = assertThrows(PictureException.class,
            () -> service.approve(pending.getId(), UUID.randomUUID()));
        assertEquals(409, ex.getStatusCode());
        verify(pictureRepository, never()).save(any());
    }

    @Test
    void reject_setsRejected_andRetainsObject() {
        UUID adminId = UUID.randomUUID();
        Picture uploaded = uploaded();
        when(pictureRepository.findById(uploaded.getId())).thenReturn(Optional.of(uploaded));
        when(pictureRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        Picture result = service.reject(uploaded.getId(), adminId, "Niet gepast");

        assertEquals(PictureStatus.REJECTED, result.getStatus());
        assertEquals("Niet gepast", result.getNotes());
        assertEquals(adminId, result.getModeratedBy());
        // Rejected objects are kept for audit.
        verify(storageService, never()).delete(any());
        verify(webSocketPublisher).sendToTeam(eq(gameId), eq(teamId), eq("PICTURE_MODERATED"), any());
        verify(webSocketPublisher).sendToAdminTopic(eq(gameId), eq("pictures"), eq("PICTURE_MODERATED"), any());
        // Rejected pictures never reach the photowall.
        verify(photoBroadcastBuffer, never()).buffer(any(), any());
    }

    @Test
    void reject_unknownPicture_with404() {
        UUID id = UUID.randomUUID();
        when(pictureRepository.findById(id)).thenReturn(Optional.empty());

        PictureException ex = assertThrows(PictureException.class,
            () -> service.reject(id, UUID.randomUUID(), "x"));
        assertEquals(404, ex.getStatusCode());
    }

    // ==================== response mapping (team attribution) ====================

    @Test
    void toPublicResponse_attributesToUploadingTeam() {
        Picture p = uploaded();
        when(teamRepository.findById(teamId)).thenReturn(Optional.of(team));

        var dto = service.toPublicResponse(p);

        assertEquals("De Watervlooien", dto.teamName());
        assertEquals("Snelle en wendbare vlettenvaarders", dto.teamDescription());
    }

    @Test
    void toResponse_adminUpload_hasNullTeamFields() {
        Picture adminPic = uploaded();
        adminPic.setTeamId(null); // admin uploads have no owning team

        var dto = service.toResponse(adminPic);

        assertNull(dto.teamName());
        assertNull(dto.teamDescription());
        verify(teamRepository, never()).findById(any());
    }

    @Test
    void toPublicResponses_resolvesEachTeamOnce() {
        Picture a = uploaded();
        Picture b = uploaded();
        when(teamRepository.findById(teamId)).thenReturn(Optional.of(team));

        var dtos = service.toPublicResponses(List.of(a, b));

        assertEquals(2, dtos.size());
        assertTrue(dtos.stream().allMatch(d -> "De Watervlooien".equals(d.teamName())));
        // Both pictures share a team, so it is looked up exactly once.
        verify(teamRepository).findById(teamId);
    }

    // ==================== viewUrlFor ====================

    @Test
    void viewUrlFor_returnsNull_whenNotUploaded() {
        assertNull(service.viewUrlFor(reservation()));
        verify(storageService, never()).presignView(any(), any());
    }

    @Test
    void viewUrlFor_presigns_whenUploaded() {
        Picture uploaded = uploaded();
        when(storageService.presignView(uploaded.getObjectKey(), Duration.ofHours(1)))
            .thenReturn("https://storj.example/view");

        assertEquals("https://storj.example/view", service.viewUrlFor(uploaded));
    }

    // ==================== helpers ====================

    private Game gameWith(Game.GameStatus status) {
        Game game = new Game();
        game.setId(gameId);
        game.setStatus(status);
        return game;
    }

    // ==================== game-scoped listing ====================

    @Test
    void findReadyForModerationByGame_delegatesToRepository() {
        List<Picture> expected = List.of(uploaded());
        when(pictureRepository.findReadyForModerationByGameId(gameId)).thenReturn(expected);
        assertEquals(expected, service.findReadyForModerationByGame(gameId));
    }

    @Test
    void findByStatusAndGame_delegatesToRepository() {
        List<Picture> expected = List.of(uploaded());
        when(pictureRepository.findByStatusAndGameId(PictureStatus.APPROVED, gameId)).thenReturn(expected);
        assertEquals(expected, service.findByStatusAndGame(PictureStatus.APPROVED, gameId));
    }

    @Test
    void findAllByGame_delegatesToRepository() {
        List<Picture> expected = List.of(uploaded());
        when(pictureRepository.findAllByGameId(gameId)).thenReturn(expected);
        assertEquals(expected, service.findAllByGame(gameId));
    }

    // ==================== findApprovedForTeamGame ====================

    @Test
    void findApprovedForTeamGame_resolvesTeamGame_andDelegatesWithCap() {
        List<Picture> expected = List.of(approvedFor(gameId));
        when(teamRepository.findById(teamId)).thenReturn(Optional.of(team));
        when(pictureRepository.findRecentApprovedByGameId(gameId, 60)).thenReturn(expected);

        assertEquals(expected, service.findApprovedForTeamGame(teamId));
        // A team only ever sees its own game's approved pictures, capped to the wall limit.
        verify(pictureRepository).findRecentApprovedByGameId(gameId, 60);
    }

    @Test
    void findApprovedForTeamGame_unknownTeam_with404() {
        when(teamRepository.findById(teamId)).thenReturn(Optional.empty());

        PictureException ex = assertThrows(PictureException.class,
            () -> service.findApprovedForTeamGame(teamId));
        assertEquals(404, ex.getStatusCode());
        verify(pictureRepository, never()).findApprovedByGameId(any());
    }

    // ==================== findApprovedForLatestGame ====================

    @Test
    void findApprovedForLatestGame_emptyWhenNoneApproved() {
        when(pictureRepository.findByStatus(PictureStatus.APPROVED)).thenReturn(List.of());
        assertTrue(service.findApprovedForLatestGame().isEmpty());
    }

    @Test
    void findApprovedForLatestGame_keepsOnlyTheMostRecentGame() {
        UUID latestGame = UUID.randomUUID();
        UUID olderGame = UUID.randomUUID();
        // findByStatus returns newest first, so the first element's game is the latest.
        Picture newest = approvedFor(latestGame);
        Picture alsoLatest = approvedFor(latestGame);
        Picture older = approvedFor(olderGame);
        when(pictureRepository.findByStatus(PictureStatus.APPROVED))
            .thenReturn(List.of(newest, alsoLatest, older));

        List<Picture> result = service.findApprovedForLatestGame();

        assertEquals(2, result.size());
        assertTrue(result.stream().allMatch(p -> latestGame.equals(p.getGameId())));
    }

    // ==================== approved archive ====================

    @Test
    void writeApprovedArchive_namesEntriesAfterTheUploadingTeam() throws Exception {
        UUID otherTeamId = UUID.randomUUID();
        Team otherTeam = new Team();
        otherTeam.setId(otherTeamId);
        otherTeam.setGameId(gameId);
        otherTeam.setName("Windkracht Tien");

        Picture first = archived(teamId, "image/jpeg", Instant.parse("2026-07-28T10:00:00Z"));
        Picture second = archived(teamId, "image/png", Instant.parse("2026-07-28T11:00:00Z"));
        Picture other = archived(otherTeamId, "image/webp", Instant.parse("2026-07-28T09:00:00Z"));
        Picture adminUpload = archived(null, "image/jpeg", Instant.parse("2026-07-28T12:00:00Z"));

        when(pictureRepository.findApprovedByGameId(gameId))
            .thenReturn(List.of(second, other, adminUpload, first));
        when(teamRepository.findById(teamId)).thenReturn(Optional.of(team));
        when(teamRepository.findById(otherTeamId)).thenReturn(Optional.of(otherTeam));
        when(storageService.openObject(any())).thenAnswer(i -> Optional.of(new ByteArrayInputStream(BYTES)));

        assertEquals(
            List.of(
                "de-watervlooien-01.jpg",   // oldest of the two Watervlooien pictures first
                "de-watervlooien-02.png",
                "organisatie-01.jpg",       // admin upload has no team
                "windkracht-tien-01.webp"),
            entryNamesOf(archiveOf(gameId)));
    }

    @Test
    void writeApprovedArchive_skipsPicturesWhoseObjectIsGone() throws Exception {
        Picture present = archived(teamId, "image/jpeg", Instant.parse("2026-07-28T10:00:00Z"));
        Picture missing = archived(teamId, "image/jpeg", Instant.parse("2026-07-28T11:00:00Z"));

        when(pictureRepository.findApprovedByGameId(gameId)).thenReturn(List.of(present, missing));
        when(teamRepository.findById(teamId)).thenReturn(Optional.of(team));
        when(storageService.openObject(present.getObjectKey()))
            .thenReturn(Optional.of(new ByteArrayInputStream(BYTES)));
        when(storageService.openObject(missing.getObjectKey())).thenReturn(Optional.empty());

        // The archive stays readable; the missing picture simply is not in it.
        assertEquals(List.of("de-watervlooien-01.jpg"), entryNamesOf(archiveOf(gameId)));
    }

    @Test
    void writeApprovedArchive_excludesPicturesThatWereNeverUploaded() throws Exception {
        Picture uploaded = archived(teamId, "image/jpeg", Instant.parse("2026-07-28T10:00:00Z"));
        Picture reserved = archived(teamId, "image/jpeg", Instant.parse("2026-07-28T11:00:00Z"));
        reserved.setUploadedAt(null);

        when(pictureRepository.findApprovedByGameId(gameId)).thenReturn(List.of(uploaded, reserved));
        when(teamRepository.findById(teamId)).thenReturn(Optional.of(team));
        when(storageService.openObject(uploaded.getObjectKey()))
            .thenReturn(Optional.of(new ByteArrayInputStream(BYTES)));

        assertEquals(List.of("de-watervlooien-01.jpg"), entryNamesOf(archiveOf(gameId)));
        verify(storageService, never()).openObject(reserved.getObjectKey());
    }

    @Test
    void writeApprovedArchive_copiesTheStoredBytes() throws Exception {
        Picture picture = archived(teamId, "image/jpeg", Instant.parse("2026-07-28T10:00:00Z"));
        when(pictureRepository.findApprovedByGameId(gameId)).thenReturn(List.of(picture));
        when(teamRepository.findById(teamId)).thenReturn(Optional.of(team));
        when(storageService.openObject(picture.getObjectKey()))
            .thenReturn(Optional.of(new ByteArrayInputStream(BYTES)));

        try (ZipInputStream zip = new ZipInputStream(new ByteArrayInputStream(archiveOf(gameId)))) {
            assertNotNull(zip.getNextEntry());
            assertArrayEquals(BYTES, zip.readAllBytes());
        }
    }

    @Test
    void approvedArchiveName_sluggifiesTheGameName() {
        Game game = new Game();
        game.setId(gameId);
        game.setName("Vlettenrally 2026 — Kaag");
        when(gameRepository.findById(gameId)).thenReturn(Optional.of(game));

        assertEquals("fotos-vlettenrally-2026-kaag.zip", service.approvedArchiveName(gameId));
    }

    @Test
    void approvedArchiveName_fallsBackToTheGameIdWhenTheNameIsUnusable() {
        when(gameRepository.findById(gameId)).thenReturn(Optional.empty());

        assertEquals("fotos-" + gameId + ".zip", service.approvedArchiveName(gameId));
    }

    /** Bytes stood in for every stored object; content is irrelevant, identity is not. */
    private static final byte[] BYTES = "picture-bytes".getBytes(StandardCharsets.UTF_8);

    private byte[] archiveOf(UUID game) throws Exception {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        service.writeApprovedArchive(game, out);
        return out.toByteArray();
    }

    private static List<String> entryNamesOf(byte[] archive) throws Exception {
        List<String> names = new java.util.ArrayList<>();
        try (ZipInputStream zip = new ZipInputStream(new ByteArrayInputStream(archive))) {
            for (ZipEntry entry = zip.getNextEntry(); entry != null; entry = zip.getNextEntry()) {
                names.add(entry.getName());
            }
        }
        return names;
    }

    private Picture archived(UUID owner, String contentType, Instant createdAt) {
        Picture p = new Picture();
        p.setTeamId(owner);
        p.setGameId(gameId);
        p.setStatus(PictureStatus.APPROVED);
        p.setContentType(contentType);
        p.setCreatedAt(createdAt);
        p.setUploadedAt(createdAt);
        p.setObjectKey("games/" + gameId + "/" + p.getId());
        return p;
    }

    private Picture approvedFor(UUID game) {
        Picture p = new Picture();
        p.setGameId(game);
        p.setStatus(PictureStatus.APPROVED);
        p.setUploadedAt(java.time.Instant.now());
        return p;
    }

    private Picture reservation() {
        Picture p = new Picture();
        p.setTeamId(teamId);
        p.setGameId(gameId);
        p.setStatus(PictureStatus.PENDING);
        p.setContentType("image/jpeg");
        p.setObjectKey("games/" + gameId + "/teams/" + teamId + "/" + p.getId());
        return p;
    }

    private Picture uploaded() {
        Picture p = reservation();
        p.setUploadedAt(java.time.Instant.now());
        p.setSizeBytes(512_000L);
        return p;
    }
}
