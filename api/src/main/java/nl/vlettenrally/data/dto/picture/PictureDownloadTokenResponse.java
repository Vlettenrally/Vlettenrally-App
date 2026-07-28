package nl.vlettenrally.data.dto.picture;

import io.quarkus.runtime.annotations.RegisterForReflection;

/**
 * One-time token authorising a download of a game's approved-picture archive, plus how many
 * seconds it stays valid.
 */
@RegisterForReflection
public record PictureDownloadTokenResponse(
    String token,
    int expiresIn
) {}
