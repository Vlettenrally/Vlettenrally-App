package nl.vlettenrally.service;

import nl.vlettenrally.data.domain.AdminNotification;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface AdminInboxService {
    AdminNotification createNotification(UUID gameId, UUID teamId, String source, String content, String metadataJson);

    Optional<AdminNotification> findById(UUID notificationId);

    List<AdminNotification> findByGame(UUID gameId, int page, int pageSize);

    long countByGame(UUID gameId);

    void markAsRead(UUID notificationId, UUID adminId);

    void markAllAsRead(UUID gameId, UUID adminId);
}
