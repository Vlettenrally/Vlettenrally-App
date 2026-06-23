package nl.vlettenrally.data.dto.picture;

import java.util.UUID;

import io.quarkus.runtime.annotations.RegisterForReflection;
import nl.vlettenrally.data.domain.Picture;

/**
 * An approved picture as exposed by the public gallery API.
 * <p>
 * A deliberately minimal projection: it omits moderation-only fields (team, status, notes,
 * moderator) that an external, token-authenticated consumer has no business seeing.
 * {@code viewUrl} is the cached long-lived pre-signed GET URL.
 */
@RegisterForReflection
public record PublicPictureResponse(
    UUID id,
    UUID gameId,
    String contentType,
    String viewUrl,
    String uploadedAt
) {
    public static PublicPictureResponse from(Picture p, String viewUrl) {
        return new PublicPictureResponse(
            p.getId(),
            p.getGameId(),
            p.getContentType(),
            viewUrl,
            p.getUploadedAt() != null ? p.getUploadedAt().toString() : null
        );
    }
}
