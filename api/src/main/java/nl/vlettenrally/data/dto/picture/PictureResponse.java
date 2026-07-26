package nl.vlettenrally.data.dto.picture;

import java.util.UUID;

import io.quarkus.runtime.annotations.RegisterForReflection;
import nl.vlettenrally.data.domain.Picture;
import nl.vlettenrally.data.domain.Team;

/**
 * A team picture as seen by teams (their own) or admins (moderation).
 * {@code viewUrl} is a short-lived pre-signed GET URL, present only for uploaded pictures.
 * {@code teamName}/{@code teamDescription} attribute the photo to the uploading team (the
 * scouting group); both are {@code null} for admin-uploaded photos.
 */
@RegisterForReflection
public record PictureResponse(
    UUID id,
    UUID teamId,
    UUID gameId,
    String status,
    String contentType,
    Long sizeBytes,
    String viewUrl,
    String createdAt,
    String uploadedAt,
    String moderatedAt,
    String notes,
    String teamName,
    String teamDescription
) {
    /**
     * Build a response from a domain picture, a (possibly null) pre-signed view URL, and the
     * (possibly null) owning team. Shared by the REST resource and the WebSocket payloads so both
     * emit the same shape.
     */
    public static PictureResponse from(Picture p, String viewUrl, Team team) {
        return new PictureResponse(
            p.getId(),
            p.getTeamId(),
            p.getGameId(),
            p.getStatus().name(),
            p.getContentType(),
            p.getSizeBytes(),
            viewUrl,
            p.getCreatedAt() != null ? p.getCreatedAt().toString() : null,
            p.getUploadedAt() != null ? p.getUploadedAt().toString() : null,
            p.getModeratedAt() != null ? p.getModeratedAt().toString() : null,
            p.getNotes(),
            team != null ? team.getName() : null,
            team != null ? team.getDescription() : null
        );
    }
}
