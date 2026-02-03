package nl.vlettenrally.data.dto.message;

import java.util.List;

import io.quarkus.runtime.annotations.RegisterForReflection;

/**
 * Paginated response for admin messages.
 */
@RegisterForReflection
public record AdminMessageListResponse(
    List<AdminMessageResponse> messages,
    int page,
    int pageSize,
    long totalCount,
    int totalPages
) {}
