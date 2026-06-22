package nl.vlettenrally.service.impl;

import nl.vlettenrally.data.domain.Game;
import nl.vlettenrally.data.domain.Picture;
import nl.vlettenrally.data.domain.Team;
import nl.vlettenrally.data.enums.PictureStatus;
import nl.vlettenrally.data.repository.GameRepository;
import nl.vlettenrally.data.repository.PictureRepository;
import nl.vlettenrally.data.repository.TeamRepository;
import nl.vlettenrally.service.PictureException;
import nl.vlettenrally.service.PictureService;
import nl.vlettenrally.service.storage.PictureStorageService;
import nl.vlettenrally.websockets.WebSocketPublisher;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
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

    @InjectMocks PictureServiceImpl service;

    private UUID teamId;
    private UUID gameId;
    private Team team;

    @BeforeEach
    void setUp() {
        // @ConfigProperty fields are package-private; set them directly for the unit test.
        service.maxPerTeam = 5;
        service.maxBytes = 10 * 1024 * 1024;
        service.allowedContentTypes = List.of("image/jpeg", "image/png", "image/webp");
        service.uploadUrlTtl = Duration.ofMinutes(15);
        service.viewUrlTtl = Duration.ofHours(1);

        teamId = UUID.randomUUID();
        gameId = UUID.randomUUID();
        team = new Team();
        team.setId(teamId);
        team.setGameId(gameId);
        team.setName("De Watervlooien");
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
        verify(webSocketPublisher).sendToAdmins(eq(gameId), eq("PICTURE_PENDING"), any());
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
        verify(webSocketPublisher).sendToAdmins(eq(gameId), eq("PICTURE_MODERATED"), any());
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
        verify(webSocketPublisher).sendToAdmins(eq(gameId), eq("PICTURE_MODERATED"), any());
    }

    @Test
    void reject_unknownPicture_with404() {
        UUID id = UUID.randomUUID();
        when(pictureRepository.findById(id)).thenReturn(Optional.empty());

        PictureException ex = assertThrows(PictureException.class,
            () -> service.reject(id, UUID.randomUUID(), "x"));
        assertEquals(404, ex.getStatusCode());
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
