package nl.vlettenrally.data.dto.message;

import java.util.List;
import java.util.Set;
import java.util.UUID;

import io.quarkus.runtime.annotations.RegisterForReflection;

/**
 * Response DTO for admin message with delivery status information.
 */
@RegisterForReflection
public record AdminMessageResponse(
    UUID id,
    UUID gameId,
    String messageType,
    String content,
    String icon,
    String sentAt,
    String source,
    UUID sentByAdminId,
    int totalRecipients,
    int deliveredCount,
    int failedCount,
    List<DeliveryDetail> deliveries
) {
    public record DeliveryDetail(
        UUID teamId,
        String teamName,
        String boatNumber,
        boolean delivered,
        Set<String> transmittedVia,
        String transmissionError
    ) {}
}
