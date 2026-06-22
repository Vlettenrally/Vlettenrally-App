package nl.vlettenrally.data.dto.picture;

import io.quarkus.runtime.annotations.RegisterForReflection;

/**
 * Optional notes (reason) recorded when an admin rejects a picture.
 */
@RegisterForReflection
public record PictureRejectRequest(
    String notes
) {}
