package nl.vlettenrally.data.dto.picture;

import io.quarkus.runtime.annotations.RegisterForReflection;

import java.util.List;

/**
 * One page of the players' photowall. Same cursor contract as {@link PictureListResponse}, but
 * carrying the minimal public projection — one team must not see another team's moderation data.
 */
@RegisterForReflection
public record PublicPictureListResponse(
    List<PublicPictureResponse> pictures,
    String nextCursorAt,
    String nextCursorId,
    boolean hasMore
) {}
