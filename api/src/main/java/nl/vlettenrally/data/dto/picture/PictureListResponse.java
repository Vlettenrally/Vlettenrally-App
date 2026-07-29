package nl.vlettenrally.data.dto.picture;

import io.quarkus.runtime.annotations.RegisterForReflection;

import java.util.List;

/**
 * One page of admin picture results.
 * <p>
 * Deliberately unlike the {@code page}/{@code pageSize}/{@code totalCount} envelope the other
 * paginated endpoints use: those back a jump-to-page table, this backs an infinite scroll. Photos
 * are inserted live during a rally and moderating one removes it from the pending set, so an
 * offset would re-serve or silently skip rows. The client just echoes {@code nextCursorAt} and
 * {@code nextCursorId} back to fetch the next page, and stops when {@code hasMore} is false.
 */
@RegisterForReflection
public record PictureListResponse(
    List<PictureResponse> pictures,
    String nextCursorAt,
    String nextCursorId,
    boolean hasMore
) {}
