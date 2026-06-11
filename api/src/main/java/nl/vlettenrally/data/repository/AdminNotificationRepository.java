package nl.vlettenrally.data.repository;

import nl.vlettenrally.data.domain.AdminNotification;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface AdminNotificationRepository {
    Optional<AdminNotification> findById(UUID id);

    List<AdminNotification> findByGameId(UUID gameId, int page, int pageSize);

    long countByGameId(UUID gameId);

    AdminNotification save(AdminNotification notification);

    void markAsRead(UUID notificationId, UUID readByAdminId);

    void markAllAsReadByGame(UUID gameId, UUID readByAdminId);
}
