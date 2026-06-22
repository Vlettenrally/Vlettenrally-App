package nl.vlettenrally.data.dto.picture;

import io.quarkus.runtime.annotations.RegisterForReflection;

/**
 * Request to reserve a picture upload slot and obtain a pre-signed upload URL.
 * {@code fileName} and {@code sizeBytes} are advisory (used for client-side hints);
 * {@code contentType} determines the signed content type and is validated server-side.
 */
@RegisterForReflection
public record PictureUploadRequest(
    String contentType,
    String fileName,
    Long sizeBytes
) {}
