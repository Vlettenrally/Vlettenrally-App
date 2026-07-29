package nl.vlettenrally.api.v1;

import nl.vlettenrally.data.domain.Picture;
import nl.vlettenrally.data.dto.common.ErrorResponse;
import nl.vlettenrally.data.dto.picture.PictureDownloadTokenResponse;
import nl.vlettenrally.data.dto.picture.PictureListResponse;
import nl.vlettenrally.data.dto.picture.PictureResponse;
import nl.vlettenrally.data.dto.picture.PictureUploadRequest;
import nl.vlettenrally.data.dto.picture.PictureUploadResponse;
import nl.vlettenrally.data.dto.picture.PublicPictureListResponse;
import nl.vlettenrally.data.dto.picture.PublicPictureResponse;
import nl.vlettenrally.data.enums.PictureStatus;
import nl.vlettenrally.service.PictureDownloadTokenService;
import nl.vlettenrally.service.PictureException;
import nl.vlettenrally.service.PictureService;
import nl.vlettenrally.service.PictureService.Cursor;
import nl.vlettenrally.service.PictureService.PicturePage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.SecurityContext;
import jakarta.ws.rs.core.StreamingOutput;

import java.io.ByteArrayOutputStream;
import java.security.Principal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link PictureResource}: request/response shaping and the mapping of
 * {@link PictureException} statuses onto HTTP responses.
 */
@ExtendWith(MockitoExtension.class)
class PictureResourceTest {

    @Mock PictureService pictureService;
    @Mock PictureDownloadTokenService downloadTokenService;
    @Mock SecurityContext securityContext;

    @InjectMocks PictureResource resource;

    private UUID teamId;
    private UUID pictureId;
    private UUID gameId;

    @BeforeEach
    void setUp() {
        // @ConfigProperty field is package-private; set it directly for the unit test.
        resource.photowallPageSize = 20;

        teamId = UUID.randomUUID();
        pictureId = UUID.randomUUID();
        gameId = UUID.randomUUID();
    }

    // ==================== requestUpload ====================

    @Test
    void requestUpload_returns201WithPresignedUrl() {
        Picture reserved = picture(PictureStatus.PENDING, null);
        when(pictureService.requestUpload(teamId, "image/jpeg"))
            .thenReturn(new PictureService.UploadTicket(reserved, "https://storj/upload", Instant.now()));
        when(pictureService.maxBytes()).thenReturn(10L * 1024 * 1024);
        when(pictureService.allowedContentTypes()).thenReturn(List.of("image/jpeg"));

        Response response = resource.requestUpload(teamId, new PictureUploadRequest("image/jpeg", "boat.jpg", 1234L));

        assertEquals(201, response.getStatus());
        PictureUploadResponse body = assertInstanceOf(PictureUploadResponse.class, response.getEntity());
        assertEquals(reserved.getId(), body.pictureId());
        assertEquals("https://storj/upload", body.uploadUrl());
        assertEquals(reserved.getObjectKey(), body.objectKey());
    }

    @Test
    void requestUpload_missingContentType_returns400() {
        Response response = resource.requestUpload(teamId, new PictureUploadRequest(" ", null, null));
        assertEquals(400, response.getStatus());
        assertInstanceOf(ErrorResponse.class, response.getEntity());
    }

    @Test
    void requestUpload_mapsPictureExceptionStatus() {
        when(pictureService.requestUpload(teamId, "image/jpeg"))
            .thenThrow(new PictureException(Response.Status.CONFLICT, "limit reached"));

        Response response = resource.requestUpload(teamId, new PictureUploadRequest("image/jpeg", null, null));

        assertEquals(409, response.getStatus());
    }

    // ==================== confirmUpload ====================

    @Test
    void confirmUpload_returns200WithDto() {
        Picture uploaded = picture(PictureStatus.PENDING, Instant.now());
        when(pictureService.confirmUpload(teamId, pictureId)).thenReturn(uploaded);
        when(pictureService.toResponse(uploaded)).thenReturn(response(uploaded, "https://storj/view"));

        Response response = resource.confirmUpload(teamId, pictureId);

        assertEquals(200, response.getStatus());
        PictureResponse body = assertInstanceOf(PictureResponse.class, response.getEntity());
        assertEquals("https://storj/view", body.viewUrl());
        assertEquals("PENDING", body.status());
    }

    @Test
    void confirmUpload_mapsValidationFailureStatus() {
        when(pictureService.confirmUpload(teamId, pictureId))
            .thenThrow(new PictureException(422, "too large"));

        Response response = resource.confirmUpload(teamId, pictureId);

        assertEquals(422, response.getStatus());
    }

    // ==================== delete ====================

    @Test
    void deletePicture_returns204() {
        Response response = resource.deletePicture(teamId, pictureId);
        assertEquals(204, response.getStatus());
    }

    @Test
    void deletePicture_mapsConflict() {
        org.mockito.Mockito.doThrow(new PictureException(Response.Status.CONFLICT, "not pending"))
            .when(pictureService).deleteOwnPicture(teamId, pictureId);

        Response response = resource.deletePicture(teamId, pictureId);

        assertEquals(409, response.getStatus());
    }

    // ==================== admin listing ====================

    @Test
    void getAllPictures_invalidStatus_returns400() {
        Response response = resource.getAllPictures("bogus", null, null, null, null);
        assertEquals(400, response.getStatus());
    }

    @Test
    void getAllPictures_filtersByStatus() {
        Picture approved = picture(PictureStatus.APPROVED, Instant.now());
        when(pictureService.findPage(eq(PictureStatus.APPROVED), isNull(), isNull(), anyInt()))
            .thenReturn(new PicturePage(List.of(approved), false, null));
        when(pictureService.toResponses(List.of(approved))).thenReturn(List.of(response(approved, null)));

        Response response = resource.getAllPictures("approved", null, null, null, null);

        assertEquals(200, response.getStatus());
        PictureListResponse body = assertInstanceOf(PictureListResponse.class, response.getEntity());
        assertEquals(1, body.pictures().size());
        assertFalse(body.hasMore());
        assertNull(body.nextCursorAt());
    }

    @Test
    void getAllPictures_noStatus_returnsAll() {
        when(pictureService.findPage(isNull(), isNull(), isNull(), anyInt()))
            .thenReturn(new PicturePage(List.of(), false, null));
        Response response = resource.getAllPictures(null, null, null, null, null);
        assertEquals(200, response.getStatus());
    }

    @Test
    void getAllPictures_hasMore_exposesTheNextCursor() {
        Instant at = Instant.parse("2026-07-29T10:15:30Z");
        UUID lastId = UUID.randomUUID();
        when(pictureService.findPage(isNull(), isNull(), isNull(), anyInt()))
            .thenReturn(new PicturePage(List.of(), true, new Cursor(at, lastId)));

        Response response = resource.getAllPictures(null, null, null, null, null);

        PictureListResponse body = assertInstanceOf(PictureListResponse.class, response.getEntity());
        assertTrue(body.hasMore());
        assertEquals(at.toString(), body.nextCursorAt());
        assertEquals(lastId.toString(), body.nextCursorId());
    }

    @Test
    void getAllPictures_passesTheCursorThrough() {
        UUID cursorId = UUID.randomUUID();
        when(pictureService.findPage(isNull(), isNull(), any(Cursor.class), anyInt()))
            .thenReturn(new PicturePage(List.of(), false, null));

        resource.getAllPictures(null, null, "2026-07-29T10:15:30Z", cursorId, null);

        verify(pictureService).findPage(
            isNull(), isNull(),
            eq(new Cursor(Instant.parse("2026-07-29T10:15:30Z"), cursorId)),
            anyInt());
    }

    @Test
    void getAllPictures_malformedCursor_returns400() {
        Response response = resource.getAllPictures(null, null, "yesterday", UUID.randomUUID(), null);
        assertEquals(400, response.getStatus());
        assertInstanceOf(ErrorResponse.class, response.getEntity());
    }

    @Test
    void getPendingPictures_clampsAnOutOfRangePageSize() {
        when(pictureService.findReadyForModerationPage(isNull(), isNull(), anyInt()))
            .thenReturn(new PicturePage(List.of(), false, null));

        resource.getPendingPictures(null, null, null, 5000);

        // Every picture in a response costs one presign, so an oversized page falls back to the default.
        verify(pictureService).findReadyForModerationPage(isNull(), isNull(), eq(24));
    }

    // ==================== photowall ====================

    @Test
    void getPhotowall_returns200WithPublicProjection() {
        Picture approved = picture(PictureStatus.APPROVED, Instant.now());
        when(pictureService.findApprovedForTeamGamePage(eq(teamId), isNull(), anyInt()))
            .thenReturn(new PicturePage(List.of(approved), false, null));
        when(pictureService.toPublicResponses(List.of(approved)))
            .thenReturn(List.of(publicResponse(approved, "https://storj/view")));

        Response response = resource.getPhotowall(teamId, null, null, null);

        assertEquals(200, response.getStatus());
        PublicPictureListResponse body =
            assertInstanceOf(PublicPictureListResponse.class, response.getEntity());
        assertEquals(1, body.pictures().size());
        // A shared wall carries team attribution (name/description) but not other teams'
        // moderation metadata (status, notes, moderator).
        PublicPictureResponse dto = body.pictures().getFirst();
        assertEquals(approved.getId(), dto.id());
        assertEquals("https://storj/view", dto.viewUrl());
    }

    @Test
    void getPhotowall_mapsPictureExceptionStatus() {
        when(pictureService.findApprovedForTeamGamePage(eq(teamId), isNull(), anyInt()))
            .thenThrow(new PictureException(Response.Status.NOT_FOUND, "Team not found"));

        Response response = resource.getPhotowall(teamId, null, null, null);

        assertEquals(404, response.getStatus());
        assertInstanceOf(ErrorResponse.class, response.getEntity());
    }

    // ==================== moderation ====================

    @Test
    void approve_passesAdminIdFromPrincipal() {
        UUID adminId = UUID.randomUUID();
        Picture approved = picture(PictureStatus.APPROVED, Instant.now());
        Principal principal = adminId::toString;
        when(securityContext.getUserPrincipal()).thenReturn(principal);
        when(pictureService.approve(eq(pictureId), eq(adminId))).thenReturn(approved);
        when(pictureService.toResponse(approved)).thenReturn(response(approved, null));

        Response response = resource.approvePicture(pictureId, securityContext);

        assertEquals(200, response.getStatus());
        PictureResponse body = assertInstanceOf(PictureResponse.class, response.getEntity());
        assertEquals("APPROVED", body.status());
    }

    @Test
    void reject_passesNotes() {
        UUID adminId = UUID.randomUUID();
        Picture rejected = picture(PictureStatus.REJECTED, Instant.now());
        Principal principal = adminId::toString;
        when(securityContext.getUserPrincipal()).thenReturn(principal);
        when(pictureService.reject(eq(pictureId), eq(adminId), eq("nope"))).thenReturn(rejected);
        when(pictureService.toResponse(rejected)).thenReturn(response(rejected, null));

        Response response = resource.rejectPicture(pictureId,
            new nl.vlettenrally.data.dto.picture.PictureRejectRequest("nope"), securityContext);

        assertEquals(200, response.getStatus());
        assertEquals("REJECTED", ((PictureResponse) response.getEntity()).status());
    }

    // ==================== archive download ====================

    @Test
    void requestDownloadToken_returnsTokenWithItsLifetime() {
        when(downloadTokenService.issue(gameId)).thenReturn("tok123");
        when(downloadTokenService.ttlSeconds()).thenReturn(60);

        Response response = resource.requestDownloadToken(gameId);

        assertEquals(200, response.getStatus());
        PictureDownloadTokenResponse body =
            assertInstanceOf(PictureDownloadTokenResponse.class, response.getEntity());
        assertEquals("tok123", body.token());
        assertEquals(60, body.expiresIn());
    }

    @Test
    void requestDownloadToken_rejectsAMissingGameId() {
        Response response = resource.requestDownloadToken(null);

        assertEquals(400, response.getStatus());
        assertInstanceOf(ErrorResponse.class, response.getEntity());
    }

    @Test
    void requestDownloadToken_returns500WhenTheTokenCannotBeStored() {
        when(downloadTokenService.issue(gameId)).thenThrow(new IllegalStateException("valkey down"));

        Response response = resource.requestDownloadToken(gameId);

        assertEquals(500, response.getStatus());
        assertInstanceOf(ErrorResponse.class, response.getEntity());
    }

    @Test
    void downloadApprovedArchive_streamsTheGameFromTheToken() throws Exception {
        when(downloadTokenService.consume("tok123")).thenReturn(Optional.of(gameId));
        when(pictureService.approvedArchiveName(gameId)).thenReturn("fotos-kaag.zip");

        Response response = resource.downloadApprovedArchive("tok123");

        assertEquals(200, response.getStatus());
        assertEquals("attachment; filename=\"fotos-kaag.zip\"",
            response.getHeaderString("Content-Disposition"));

        // The entity is the archive writer itself; run it to prove it is wired to the token's game.
        StreamingOutput archive = assertInstanceOf(StreamingOutput.class, response.getEntity());
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        archive.write(out);
        verify(pictureService).writeApprovedArchive(eq(gameId), eq(out));
    }

    @Test
    void downloadApprovedArchive_rejectsAnInvalidOrSpentToken() {
        when(downloadTokenService.consume("nope")).thenReturn(Optional.empty());

        Response response = resource.downloadApprovedArchive("nope");

        assertEquals(401, response.getStatus());
        assertInstanceOf(ErrorResponse.class, response.getEntity());
        // The method produces zip, so the error branch has to declare JSON itself.
        assertEquals(MediaType.APPLICATION_JSON_TYPE, response.getMediaType());
    }

    private Picture picture(PictureStatus status, Instant uploadedAt) {
        Picture p = new Picture();
        p.setId(pictureId);
        p.setTeamId(teamId);
        p.setGameId(gameId);
        p.setStatus(status);
        p.setContentType("image/jpeg");
        p.setObjectKey("teams/" + teamId + "/" + pictureId);
        p.setUploadedAt(uploadedAt);
        return p;
    }

    private PictureResponse response(Picture p, String viewUrl) {
        return PictureResponse.from(p, viewUrl, null);
    }

    private PublicPictureResponse publicResponse(Picture p, String viewUrl) {
        return PublicPictureResponse.from(p, viewUrl, null);
    }
}
