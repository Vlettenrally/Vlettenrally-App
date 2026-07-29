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
import nl.vlettenrally.service.AutomationEngine;
import nl.vlettenrally.service.PictureException;
import nl.vlettenrally.service.PictureService;
import nl.vlettenrally.data.dto.picture.PublicPictureResponse;
import nl.vlettenrally.service.storage.PictureStorageService;
import nl.vlettenrally.websockets.AdminTopics;
import nl.vlettenrally.websockets.PhotoBroadcastBuffer;
import nl.vlettenrally.websockets.WebSocketPublisher;
import nl.vlettenrally.websockets.dto.WebSocketMessageType;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.text.Normalizer;
import java.time.Duration;
import java.time.Instant;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.zip.Deflater;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

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

    @Inject
    AutomationEngine automationEngine;

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
    public PicturePage findReadyForModerationPage(UUID gameId, Cursor cursor, int pageSize) {
        return pageOf(
            pictureRepository.findReadyForModerationPage(gameId, at(cursor), id(cursor), pageSize + 1),
            pageSize, Picture::getUploadedAt);
    }

    @Override
    public PicturePage findPage(PictureStatus status, UUID gameId, Cursor cursor, int pageSize) {
        return pageOf(
            pictureRepository.findPage(status, gameId, at(cursor), id(cursor), pageSize + 1),
            pageSize, Picture::getCreatedAt);
    }

    @Override
    public PicturePage findApprovedPage(UUID gameId, Cursor cursor, int pageSize) {
        return pageOf(
            pictureRepository.findApprovedPage(gameId, at(cursor), id(cursor), pageSize + 1),
            pageSize, Picture::getUploadedAt);
    }

    @Override
    public List<Picture> findApprovedByGame(UUID gameId) {
        return pictureRepository.findApprovedByGameId(gameId);
    }

    @Override
    public List<Picture> findAllApproved() {
        return pictureRepository.findByStatus(PictureStatus.APPROVED);
    }

    @Override
    public PicturePage findApprovedForTeamGamePage(UUID teamId, Cursor cursor, int pageSize) {
        Team team = teamRepository.findById(teamId)
            .orElseThrow(() -> new PictureException(Response.Status.NOT_FOUND, "Team not found: " + teamId));
        return findApprovedPage(team.getGameId(), cursor, pageSize);
    }

    private static Instant at(Cursor cursor) {
        return cursor != null ? cursor.at() : null;
    }

    private static UUID id(Cursor cursor) {
        return cursor != null ? cursor.id() : null;
    }

    /**
     * Turn the {@code pageSize + 1} rows the repository was asked for into a page: an extra row
     * means there is more to fetch, and the cursor for the next page is the sort key of the last
     * row we actually return.
     */
    private static PicturePage pageOf(List<Picture> rows, int pageSize, Function<Picture, Instant> sortKey) {
        boolean hasMore = rows.size() > pageSize;
        List<Picture> page = hasMore ? rows.subList(0, pageSize) : rows;
        Cursor next = hasMore && !page.isEmpty()
            ? new Cursor(sortKey.apply(page.getLast()), page.getLast().getId())
            : null;
        return new PicturePage(List.copyOf(page), hasMore, next);
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
        PictureStatus previous = picture.getStatus();
        picture.setStatus(PictureStatus.APPROVED);
        picture.setModeratedAt(Instant.now());
        picture.setModeratedBy(adminId);
        Picture saved = moderated(pictureRepository.save(picture));
        bufferForGallery(saved);
        // Only a genuine transition into APPROVED is an event: re-approving an already
        // approved picture must not fire again. Admin uploads have no team to fire for.
        if (previous != PictureStatus.APPROVED && saved.getTeamId() != null) {
            automationEngine.onPictureApproved(saved.getGameId(), saved.getTeamId());
        }
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

    @Override
    public PictureResponse toResponse(Picture picture) {
        return PictureResponse.from(picture, viewUrlFor(picture), teamFor(picture.getTeamId()));
    }

    @Override
    public List<PictureResponse> toResponses(List<Picture> pictures) {
        Map<UUID, Optional<Team>> cache = new HashMap<>();
        return pictures.stream()
            .map(p -> PictureResponse.from(p, viewUrlFor(p), teamFor(p.getTeamId(), cache)))
            .toList();
    }

    @Override
    public PublicPictureResponse toPublicResponse(Picture picture) {
        return PublicPictureResponse.from(picture, viewUrlFor(picture), teamFor(picture.getTeamId()));
    }

    @Override
    public List<PublicPictureResponse> toPublicResponses(List<Picture> pictures) {
        Map<UUID, Optional<Team>> cache = new HashMap<>();
        return pictures.stream()
            .map(p -> PublicPictureResponse.from(p, viewUrlFor(p), teamFor(p.getTeamId(), cache)))
            .toList();
    }

    /** Resolve a (possibly null) team id to its team, or null. */
    private Team teamFor(UUID teamId) {
        return teamId != null ? teamRepository.findById(teamId).orElse(null) : null;
    }

    /** Resolve a team id via a memoized map so a list only looks up each distinct team once. */
    private Team teamFor(UUID teamId, Map<UUID, Optional<Team>> cache) {
        if (teamId == null) {
            return null;
        }
        return cache.computeIfAbsent(teamId, id -> teamRepository.findById(id)).orElse(null);
    }

    /**
     * Queue a freshly-approved picture for the batched photowall broadcast. Uses the public
     * projection so peers never receive another team's moderation metadata.
     */
    private void bufferForGallery(Picture picture) {
        photoBroadcastBuffer.buffer(picture.getGameId(), toPublicResponse(picture));
    }

    @Override
    public String approvedArchiveName(UUID gameId) {
        String name = gameRepository.findById(gameId).map(Game::getName).orElse(null);
        String slug = slug(name);
        return "fotos-" + (slug.isEmpty() ? gameId.toString() : slug) + ".zip";
    }

    @Override
    public void writeApprovedArchive(UUID gameId, OutputStream out) throws IOException {
        // Group by team so the per-team numbering runs 01, 02, ... in a predictable order.
        Map<UUID, Optional<Team>> teamCache = new HashMap<>();
        List<Picture> pictures = pictureRepository.findApprovedByGameId(gameId).stream()
            .filter(Picture::isUploaded)
            .sorted(Comparator
                .comparing((Picture p) -> archiveBaseName(p, teamCache))
                .thenComparing(Picture::getCreatedAt, Comparator.nullsLast(Comparator.naturalOrder()))
                .thenComparing(Picture::getId))
            .toList();

        ZipOutputStream zip = new ZipOutputStream(out, StandardCharsets.UTF_8);
        // JPEG/PNG/WebP are already compressed; deflating them again only burns CPU.
        zip.setLevel(Deflater.NO_COMPRESSION);

        Map<String, Integer> counters = new HashMap<>();
        Set<String> usedNames = new HashSet<>();
        for (Picture picture : pictures) {
            String base = archiveBaseName(picture, teamCache);
            int index = counters.merge(base, 1, Integer::sum);
            String entryName = uniqueName(usedNames,
                String.format("%s-%02d%s", base, index, extensionFor(picture.getContentType())));

            Optional<InputStream> object;
            try {
                object = storageService.openObject(picture.getObjectKey());
            } catch (RuntimeException e) {
                Log.warnf("Skipping picture %s in archive, storage read failed: %s",
                    picture.getId(), e.getMessage());
                continue;
            }
            if (object.isEmpty()) {
                Log.warnf("Skipping picture %s in archive, object %s is missing",
                    picture.getId(), picture.getObjectKey());
                continue;
            }

            zip.putNextEntry(new ZipEntry(entryName));
            try (InputStream in = object.get()) {
                in.transferTo(zip);
            } catch (IOException | RuntimeException e) {
                // The entry is already open and partially written - there is no way to unwind it,
                // so let the caller's stream break rather than pretend the archive is complete.
                Log.errorf("Failed to copy picture %s into archive: %s", picture.getId(), e.getMessage());
                throw e instanceof IOException io ? io : new IOException(e);
            }
            zip.closeEntry();
            // Keep bytes moving to the client instead of pooling in the response buffer.
            zip.flush();
        }

        // Writes the central directory. Deliberately not closing `out` itself - that is the
        // response stream and belongs to the container.
        zip.finish();
    }

    /**
     * Base file name for a picture's archive entry: the slugged team name, or {@code organisatie}
     * for admin uploads (and for a team that no longer exists).
     */
    private String archiveBaseName(Picture picture, Map<UUID, Optional<Team>> cache) {
        Team team = teamFor(picture.getTeamId(), cache);
        String slug = team != null ? slug(team.getName()) : "";
        return slug.isEmpty() ? "organisatie" : slug;
    }

    /**
     * Lowercase, diacritic-free, filename-safe form of a name. Dutch team names carry accents,
     * and the result also ends up in a Content-Disposition header, so it is kept to ASCII.
     */
    private static String slug(String value) {
        if (value == null) {
            return "";
        }
        String ascii = Normalizer.normalize(value, Normalizer.Form.NFD)
            .replaceAll("\\p{M}+", "");
        return ascii.toLowerCase(Locale.ROOT)
            .replaceAll("[^a-z0-9]+", "-")
            .replaceAll("^-+|-+$", "");
    }

    private static String extensionFor(String contentType) {
        String normalized = contentType != null ? contentType.trim().toLowerCase(Locale.ROOT) : "";
        return switch (normalized) {
            case "image/png" -> ".png";
            case "image/webp" -> ".webp";
            default -> ".jpg";
        };
    }

    /**
     * Guarantee a unique entry name. A duplicate would make {@link ZipOutputStream} throw
     * mid-archive, after the response has already been committed.
     */
    private static String uniqueName(Set<String> used, String name) {
        String candidate = name;
        int suffix = 2;
        while (!used.add(candidate)) {
            int dot = name.lastIndexOf('.');
            candidate = name.substring(0, dot) + "-" + suffix++ + name.substring(dot);
        }
        return candidate;
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
