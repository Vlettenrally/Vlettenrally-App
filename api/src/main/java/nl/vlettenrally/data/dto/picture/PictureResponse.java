package nl.vlettenrally.data.dto.picture;

import java.util.UUID;

import io.quarkus.runtime.annotations.RegisterForReflection;
import nl.vlettenrally.data.domain.Picture;

/**
 * A team picture as seen by teams (their own) or admins (moderation).
 * {@code viewUrl} is a short-lived pre-signed GET URL, present only for uploaded pictures.
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
    String notes
) {
    /**
     * Build a response from a domain picture and a (possibly null) pre-signed view URL.
     * Shared by the REST resource and the WebSocket payloads so both emit the same shape.
     */
    public static PictureResponse from(Picture p, String viewUrl) {
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
            p.getNotes()
        );
    }
}
