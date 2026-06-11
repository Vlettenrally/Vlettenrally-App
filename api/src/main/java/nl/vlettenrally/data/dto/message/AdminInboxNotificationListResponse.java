package nl.vlettenrally.data.dto.message;

import io.quarkus.runtime.annotations.RegisterForReflection;

import java.util.List;

@RegisterForReflection
public record AdminInboxNotificationListResponse(
    List<AdminInboxNotificationResponse> notifications,
    int page,
    int pageSize,
    long totalCount,
    int totalPages
) {}
