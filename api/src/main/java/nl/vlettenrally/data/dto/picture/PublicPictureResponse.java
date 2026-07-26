package nl.vlettenrally.data.dto.picture;

import java.util.UUID;

import io.quarkus.runtime.annotations.RegisterForReflection;
import nl.vlettenrally.data.domain.Picture;
import nl.vlettenrally.data.domain.Team;

/**
 * An approved picture as exposed by the public gallery API.
 * <p>
 * A deliberately minimal projection: it attributes the photo to the uploading team by
 * {@code teamName}/{@code teamDescription} (the scouting group), but still omits moderation-only
 * fields (status, notes, moderator) that an external, token-authenticated consumer has no business
 * seeing. Team fields are {@code null} for admin-uploaded photos (no owning team).
 * {@code viewUrl} is the cached long-lived pre-signed GET URL.
 */
@RegisterForReflection
public record PublicPictureResponse(
    UUID id,
    UUID gameId,
    String contentType,
    String viewUrl,
    String uploadedAt,
    String teamName,
    String teamDescription
) {
    public static PublicPictureResponse from(Picture p, String viewUrl, Team team) {
        return new PublicPictureResponse(
            p.getId(),
            p.getGameId(),
            p.getContentType(),
            viewUrl,
            p.getUploadedAt() != null ? p.getUploadedAt().toString() : null,
            team != null ? team.getName() : null,
            team != null ? team.getDescription() : null
        );
    }
}
