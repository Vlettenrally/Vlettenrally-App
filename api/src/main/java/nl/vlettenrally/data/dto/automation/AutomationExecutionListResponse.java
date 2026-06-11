package nl.vlettenrally.data.dto.automation;

import io.quarkus.runtime.annotations.RegisterForReflection;

import java.util.List;

/**
 * Paginated response for a rule's execution log.
 */
@RegisterForReflection
public record AutomationExecutionListResponse(
    List<AutomationExecutionResponse> executions,
    int page,
    int pageSize,
    long totalCount,
    int totalPages
) {}
