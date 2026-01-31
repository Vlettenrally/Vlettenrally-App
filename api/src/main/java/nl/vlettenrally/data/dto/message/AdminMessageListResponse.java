package nl.vlettenrally.data.dto.message;

import java.util.List;

/**
 * Paginated response for admin messages.
 */
public record AdminMessageListResponse(
    List<AdminMessageResponse> messages,
    int page,
    int pageSize,
    long totalCount,
    int totalPages
) {}
