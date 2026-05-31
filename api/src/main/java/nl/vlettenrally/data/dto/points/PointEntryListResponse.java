package nl.vlettenrally.data.dto.points;

import io.quarkus.runtime.annotations.RegisterForReflection;

import java.util.List;

/**
 * Paginated response for point ledger entries.
 */
@RegisterForReflection
public record PointEntryListResponse(
    List<PointEntryResponse> entries,
    int page,
    int pageSize,
    long totalCount,
    int totalPages
) {}
