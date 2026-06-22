package nl.vlettenrally.data.dto.picture;

import java.util.List;
import java.util.UUID;

import io.quarkus.runtime.annotations.RegisterForReflection;

/**
 * Response to an upload reservation: the pre-signed PUT URL the browser uploads to,
 * plus the constraints the client should enforce before uploading.
 */
@RegisterForReflection
public record PictureUploadResponse(
    UUID pictureId,
    String objectKey,
    String uploadUrl,
    String contentType,
    String expiresAt,
    long maxBytes,
    List<String> allowedContentTypes
) {}
