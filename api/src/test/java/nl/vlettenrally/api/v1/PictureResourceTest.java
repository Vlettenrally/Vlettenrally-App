package nl.vlettenrally.api.v1;

import nl.vlettenrally.data.domain.Picture;
import nl.vlettenrally.data.dto.common.ErrorResponse;
import nl.vlettenrally.data.dto.picture.PictureResponse;
import nl.vlettenrally.data.dto.picture.PictureUploadRequest;
import nl.vlettenrally.data.dto.picture.PictureUploadResponse;
import nl.vlettenrally.data.dto.picture.PublicPictureResponse;
import nl.vlettenrally.data.enums.PictureStatus;
import nl.vlettenrally.service.PictureException;
import nl.vlettenrally.service.PictureService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.SecurityContext;

import java.security.Principal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link PictureResource}: request/response shaping and the mapping of
 * {@link PictureException} statuses onto HTTP responses.
 */
@ExtendWith(MockitoExtension.class)
class PictureResourceTest {

    @Mock PictureService pictureService;
    @Mock SecurityContext securityContext;

    @InjectMocks PictureResource resource;

    private UUID teamId;
    private UUID pictureId;
    private UUID gameId;

    @BeforeEach
    void setUp() {
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
        when(pictureService.viewUrlFor(uploaded)).thenReturn("https://storj/view");

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
        Response response = resource.getAllPictures("bogus", null);
        assertEquals(400, response.getStatus());
    }

    @Test
    void getAllPictures_filtersByStatus() {
        when(pictureService.findByStatus(PictureStatus.APPROVED))
            .thenReturn(List.of(picture(PictureStatus.APPROVED, Instant.now())));

        Response response = resource.getAllPictures("approved", null);

        assertEquals(200, response.getStatus());
        List<?> body = assertInstanceOf(List.class, response.getEntity());
        assertEquals(1, body.size());
    }

    @Test
    void getAllPictures_noStatus_returnsAll() {
        when(pictureService.findAll()).thenReturn(List.of());
        Response response = resource.getAllPictures(null, null);
        assertEquals(200, response.getStatus());
    }

    // ==================== photowall ====================

    @Test
    void getPhotowall_returns200WithPublicProjection() {
        Picture approved = picture(PictureStatus.APPROVED, Instant.now());
        when(pictureService.findApprovedForTeamGame(teamId)).thenReturn(List.of(approved));
        when(pictureService.viewUrlFor(approved)).thenReturn("https://storj/view");

        Response response = resource.getPhotowall(teamId);

        assertEquals(200, response.getStatus());
        List<?> body = assertInstanceOf(List.class, response.getEntity());
        assertEquals(1, body.size());
        // A shared wall must not leak other teams' moderation metadata: the public
        // projection carries only id/game/contentType/viewUrl/uploadedAt.
        PublicPictureResponse dto = assertInstanceOf(PublicPictureResponse.class, body.get(0));
        assertEquals(approved.getId(), dto.id());
        assertEquals("https://storj/view", dto.viewUrl());
    }

    @Test
    void getPhotowall_mapsPictureExceptionStatus() {
        when(pictureService.findApprovedForTeamGame(teamId))
            .thenThrow(new PictureException(Response.Status.NOT_FOUND, "Team not found"));

        Response response = resource.getPhotowall(teamId);

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
        when(pictureService.viewUrlFor(approved)).thenReturn(null);

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
        when(pictureService.viewUrlFor(any())).thenReturn(null);

        Response response = resource.rejectPicture(pictureId,
            new nl.vlettenrally.data.dto.picture.PictureRejectRequest("nope"), securityContext);

        assertEquals(200, response.getStatus());
        assertEquals("REJECTED", ((PictureResponse) response.getEntity()).status());
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
}
