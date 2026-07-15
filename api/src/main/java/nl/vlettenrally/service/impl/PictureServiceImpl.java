package nl.vlettenrally.service.impl;

import io.quarkus.logging.Log;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.core.Response;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import nl.vlettenrally.data.domain.Game;
import nl.vlettenrally.data.domain.Picture;
import nl.vlettenrally.data.domain.Team;
import nl.vlettenrally.data.dto.picture.PictureResponse;
import nl.vlettenrally.data.enums.PictureStatus;
import nl.vlettenrally.data.repository.GameRepository;
import nl.vlettenrally.data.repository.PictureRepository;
import nl.vlettenrally.data.repository.TeamRepository;
import nl.vlettenrally.service.PictureException;
import nl.vlettenrally.service.PictureService;
import nl.vlettenrally.data.dto.picture.PublicPictureResponse;
import nl.vlettenrally.service.storage.PictureStorageService;
import nl.vlettenrally.websockets.AdminTopics;
import nl.vlettenrally.websockets.PhotoBroadcastBuffer;
import nl.vlettenrally.websockets.WebSocketPublisher;
import nl.vlettenrally.websockets.dto.WebSocketMessageType;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Implementation of {@link PictureService}. Orchestrates the picture repository and the
 * S3 storage abstraction; enforces the per-team cap, allowed content types and size limit.
 */
@ApplicationScoped
public class PictureServiceImpl implements PictureService {

    @Inject
    PictureRepository pictureRepository;

    @Inject
    TeamRepository teamRepository;

    @Inject
    GameRepository gameRepository;

    @Inject
    PictureStorageService storageService;

    @Inject
    WebSocketPublisher webSocketPublisher;

    @Inject
    PhotoBroadcastBuffer photoBroadcastBuffer;

    @ConfigProperty(name = "vlettenrally.pictures.max-per-team")
    long maxPerTeam;

    @ConfigProperty(name = "vlettenrally.pictures.max-attempts-per-team")
    long maxAttemptsPerTeam;

    @ConfigProperty(name = "vlettenrally.pictures.max-bytes")
    long maxBytes;

    @ConfigProperty(name = "vlettenrally.pictures.allowed-content-types")
    List<String> allowedContentTypes;

    @ConfigProperty(name = "vlettenrally.pictures.upload-url-ttl")
    Duration uploadUrlTtl;

    @ConfigProperty(name = "vlettenrally.pictures.view-url-ttl")
    Duration viewUrlTtl;

    @ConfigProperty(name = "vlettenrally.pictures.photowall-limit")
    int photowallLimit;

    private static final String PICTURE_NOT_FOUND = "Picture not found: ";

    @Override
    public UploadTicket requestUpload(UUID teamId, String contentType) {
        String normalized = contentType != null ? contentType.trim().toLowerCase() : "";
        if (!allowedContentTypes.contains(normalized)) {
            throw new PictureException(Response.Status.UNSUPPORTED_MEDIA_TYPE,
                "Unsupported content type. Allowed: " + String.join(", ", allowedContentTypes));
        }

        Team team = teamRepository.findById(teamId)
            .orElseThrow(() -> new PictureException(Response.Status.NOT_FOUND, "Team not found: " + teamId));

        // Pictures may only be uploaded while the game is ongoing (ACTIVE).
        Game game = gameRepository.findById(team.getGameId())
            .orElseThrow(() -> new PictureException(Response.Status.NOT_FOUND, "Game not found: " + team.getGameId()));
        if (game.getStatus() != Game.GameStatus.ACTIVE) {
            throw new PictureException(Response.Status.CONFLICT,
                "Pictures can only be uploaded while the game is ongoing.");
        }

        if (pictureRepository.countAllByTeamId(teamId) >= maxAttemptsPerTeam) {
            throw new PictureException(Response.Status.CONFLICT, "Upload limit reached");
        }

        if (pictureRepository.countActiveByTeamId(teamId) >= maxPerTeam) {
            throw new PictureException(Response.Status.CONFLICT,
                "Picture limit reached (" + maxPerTeam + " per team). Delete a picture to upload a new one.");
        }

        // Reserve the row (and thus the slot + unique key) BEFORE issuing the URL.
        Picture picture = new Picture();
        picture.setTeamId(teamId);
        picture.setGameId(team.getGameId());
        picture.setStatus(PictureStatus.PENDING);
        picture.setContentType(normalized);
        // Stored under a per-game folder, then per-team, then the unique picture id.
        picture.setObjectKey("games/" + team.getGameId() + "/teams/" + teamId + "/" + picture.getId());
        Picture saved = pictureRepository.save(picture);

        String uploadUrl = storageService.presignUpload(saved.getObjectKey(), normalized, uploadUrlTtl);
        Instant expiresAt = Instant.now().plus(uploadUrlTtl);

        return new UploadTicket(saved, uploadUrl, expiresAt);
    }

    @Override
    public UploadTicket requestAdminUpload(UUID adminId, String contentType) {
        String normalized = contentType != null ? contentType.trim().toLowerCase() : "";
        if (!allowedContentTypes.contains(normalized)) {
            throw new PictureException(Response.Status.UNSUPPORTED_MEDIA_TYPE,
                "Unsupported content type. Allowed: " + String.join(", ", allowedContentTypes));
        }

        Game game = gameRepository.findActiveGames().stream().findFirst()
            .orElseThrow(() -> new PictureException(Response.Status.CONFLICT, "No active game"));

        Picture picture = new Picture();
        picture.setTeamId(null);
        picture.setGameId(game.getId());
        picture.setStatus(PictureStatus.PENDING);
        picture.setContentType(normalized);
        picture.setObjectKey("games/" + game.getId() + "/admin/" + picture.getId());
        Picture saved = pictureRepository.save(picture);

        String uploadUrl = storageService.presignUpload(saved.getObjectKey(), normalized, uploadUrlTtl);
        Instant expiresAt = Instant.now().plus(uploadUrlTtl);

        return new UploadTicket(saved, uploadUrl, expiresAt);
    }

    @Override
    public Picture confirmAdminUpload(UUID pictureId, UUID adminId) {
        Picture picture = pictureRepository.findById(pictureId)
            .orElseThrow(() -> new PictureException(Response.Status.NOT_FOUND, PICTURE_NOT_FOUND + pictureId));
        if (picture.getTeamId() != null) {
            throw new PictureException(Response.Status.NOT_FOUND, PICTURE_NOT_FOUND + pictureId);
        }

        Optional<PictureStorageService.ObjectMetadata> meta = storageService.head(picture.getObjectKey());
        if (meta.isEmpty()) {
            throw new PictureException(Response.Status.CONFLICT, "No uploaded file found for this picture");
        }

        PictureStorageService.ObjectMetadata metadata = meta.get();
        String effectiveType = metadata.contentType() != null ? metadata.contentType().toLowerCase() : picture.getContentType();

        if (metadata.sizeBytes() > maxBytes
                || effectiveType == null
                || !allowedContentTypes.contains(effectiveType)) {
            storageService.delete(picture.getObjectKey());
            pictureRepository.delete(picture.getId());
            throw new PictureException(422, "Uploaded file is too large or not an allowed image type");
        }

        picture.setUploadedAt(Instant.now());
        picture.setSizeBytes(metadata.sizeBytes());
        picture.setContentType(effectiveType);
        picture.setStatus(PictureStatus.APPROVED);
        picture.setModeratedAt(Instant.now());
        picture.setModeratedBy(adminId);
        Picture saved = pictureRepository.save(picture);

        webSocketPublisher.sendToAdminTopic(saved.getGameId(), AdminTopics.PICTURES,
            WebSocketMessageType.PICTURE_MODERATED.getValue(), toResponse(saved));
        bufferForGallery(saved);
        return saved;
    }

    @Override
    public Picture confirmUpload(UUID teamId, UUID pictureId) {
        Picture picture = ownedPicture(teamId, pictureId);

        Optional<PictureStorageService.ObjectMetadata> meta = storageService.head(picture.getObjectKey());
        if (meta.isEmpty()) {
            throw new PictureException(Response.Status.CONFLICT, "No uploaded file found for this picture");
        }

        PictureStorageService.ObjectMetadata metadata = meta.get();
        String effectiveType = metadata.contentType() != null ? metadata.contentType().toLowerCase() : picture.getContentType();

        if (metadata.sizeBytes() > maxBytes
                || effectiveType == null
                || !allowedContentTypes.contains(effectiveType)) {
            // Invalid upload: drop the object and the reservation so the slot is freed.
            storageService.delete(picture.getObjectKey());
            pictureRepository.delete(picture.getId());
            throw new PictureException(422, "Uploaded file is too large or not an allowed image type");
        }

        picture.setUploadedAt(Instant.now());
        picture.setSizeBytes(metadata.sizeBytes());
        picture.setContentType(effectiveType);
        Picture saved = pictureRepository.save(picture);

        // Notify admins so the moderation queue picks up the new picture live.
        webSocketPublisher.sendToAdminTopic(saved.getGameId(), AdminTopics.PICTURES,
            WebSocketMessageType.PICTURE_PENDING.getValue(), toResponse(saved));
        return saved;
    }

    @Override
    public List<Picture> findByTeamId(UUID teamId) {
        return pictureRepository.findByTeamId(teamId);
    }

    @Override
    public void deleteAllForTeam(UUID teamId) {
        for (Picture picture : pictureRepository.findByTeamId(teamId)) {
            try {
                storageService.delete(picture.getObjectKey());
            } catch (RuntimeException e) {
                // Best-effort: a failed object delete must not block removing the team.
                Log.warnf("Failed to delete object %s while removing team %s: %s",
                    picture.getObjectKey(), teamId, e.getMessage());
            }
            pictureRepository.delete(picture.getId());
        }
    }

    @Override
    public void deleteOwnPicture(UUID teamId, UUID pictureId) {
        Picture picture = ownedPicture(teamId, pictureId);
        if (picture.getStatus() != PictureStatus.PENDING) {
            throw new PictureException(Response.Status.CONFLICT, "Only pending pictures can be deleted");
        }
        storageService.delete(picture.getObjectKey());
        pictureRepository.delete(picture.getId());
    }

    @Override
    public List<Picture> findReadyForModeration() {
        return pictureRepository.findReadyForModeration();
    }

    @Override
    public List<Picture> findReadyForModerationByGame(UUID gameId) {
        return pictureRepository.findReadyForModerationByGameId(gameId);
    }

    @Override
    public List<Picture> findByStatus(PictureStatus status) {
        return pictureRepository.findByStatus(status);
    }

    @Override
    public List<Picture> findByStatusAndGame(PictureStatus status, UUID gameId) {
        return pictureRepository.findByStatusAndGameId(status, gameId);
    }

    @Override
    public List<Picture> findAll() {
        return pictureRepository.findAll();
    }

    @Override
    public List<Picture> findAllByGame(UUID gameId) {
        return pictureRepository.findAllByGameId(gameId);
    }

    @Override
    public List<Picture> findApprovedByGame(UUID gameId) {
        return pictureRepository.findApprovedByGameId(gameId);
    }

    @Override
    public List<Picture> findApprovedForTeamGame(UUID teamId) {
        Team team = teamRepository.findById(teamId)
            .orElseThrow(() -> new PictureException(Response.Status.NOT_FOUND, "Team not found: " + teamId));
        // Capped: a shared wall only needs the most recent photos, and live approvals
        // stream in on top via PhotoBroadcastBuffer.
        return pictureRepository.findRecentApprovedByGameId(team.getGameId(), photowallLimit);
    }

    @Override
    public List<Picture> findApprovedForLatestGame() {
        // findByStatus returns approved pictures newest first, so the first element belongs to
        // the most recently active game; keep just that game's pictures (order preserved).
        List<Picture> approved = pictureRepository.findByStatus(PictureStatus.APPROVED);
        if (approved.isEmpty()) {
            return approved;
        }
        UUID latestGameId = approved.getFirst().getGameId();
        return approved.stream()
            .filter(p -> latestGameId.equals(p.getGameId()))
            .toList();
    }

    @Override
    public Picture approve(UUID pictureId, UUID adminId) {
        Picture picture = pictureRepository.findById(pictureId)
            .orElseThrow(() -> new PictureException(Response.Status.NOT_FOUND, PICTURE_NOT_FOUND + pictureId));
        if (!picture.isUploaded()) {
            throw new PictureException(Response.Status.CONFLICT, "Picture has not been uploaded yet");
        }
        picture.setStatus(PictureStatus.APPROVED);
        picture.setModeratedAt(Instant.now());
        picture.setModeratedBy(adminId);
        Picture saved = moderated(pictureRepository.save(picture));
        bufferForGallery(saved);
        return saved;
    }

    @Override
    public Picture reject(UUID pictureId, UUID adminId, String notes) {
        Picture picture = pictureRepository.findById(pictureId)
            .orElseThrow(() -> new PictureException(Response.Status.NOT_FOUND, PICTURE_NOT_FOUND + pictureId));
        picture.setStatus(PictureStatus.REJECTED);
        picture.setModeratedAt(Instant.now());
        picture.setModeratedBy(adminId);
        picture.setNotes(notes);
        // Object is intentionally retained for audit (see feature decision).
        return moderated(pictureRepository.save(picture));
    }

    /**
     * Push a moderation result both to the owning team (so they see the new status live)
     * and to admins (so other moderators' queues update).
     */
    private Picture moderated(Picture picture) {
        PictureResponse payload = toResponse(picture);
        if (picture.getTeamId() != null) {
            webSocketPublisher.sendToTeam(picture.getGameId(), picture.getTeamId(),
                WebSocketMessageType.PICTURE_MODERATED.getValue(), payload);
        }
        webSocketPublisher.sendToAdminTopic(picture.getGameId(), AdminTopics.PICTURES,
            WebSocketMessageType.PICTURE_MODERATED.getValue(), payload);
        return picture;
    }

    private PictureResponse toResponse(Picture picture) {
        return PictureResponse.from(picture, viewUrlFor(picture));
    }

    /**
     * Queue a freshly-approved picture for the batched photowall broadcast. Uses the public
     * projection so peers never receive another team's moderation metadata.
     */
    private void bufferForGallery(Picture picture) {
        photoBroadcastBuffer.buffer(picture.getGameId(), PublicPictureResponse.from(picture, viewUrlFor(picture)));
    }

    @Override
    public String viewUrlFor(Picture picture) {
        if (picture == null || !picture.isUploaded()) {
            return null;
        }
        try {
            return storageService.presignView(picture.getObjectKey(), viewUrlTtl);
        } catch (RuntimeException e) {
            Log.warnf("Failed to presign view URL for %s: %s", picture.getObjectKey(), e.getMessage());
            return null;
        }
    }

    @Override
    public long maxBytes() {
        return maxBytes;
    }

    @Override
    public List<String> allowedContentTypes() {
        return allowedContentTypes;
    }

    private Picture ownedPicture(UUID teamId, UUID pictureId) {
        Picture picture = pictureRepository.findById(pictureId)
            .orElseThrow(() -> new PictureException(Response.Status.NOT_FOUND, PICTURE_NOT_FOUND + pictureId));
        if (!teamId.equals(picture.getTeamId())) {
            throw new PictureException(Response.Status.NOT_FOUND, PICTURE_NOT_FOUND + pictureId);
        }
        return picture;
    }
}
