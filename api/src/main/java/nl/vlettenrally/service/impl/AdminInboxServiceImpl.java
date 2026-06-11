package nl.vlettenrally.service.impl;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import nl.vlettenrally.data.domain.AdminNotification;
import nl.vlettenrally.data.repository.AdminNotificationRepository;
import nl.vlettenrally.service.AdminInboxService;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@ApplicationScoped
public class AdminInboxServiceImpl implements AdminInboxService {

    @Inject
    AdminNotificationRepository adminNotificationRepository;

    @Override
    public AdminNotification createNotification(UUID gameId, UUID teamId, String source, String content, String metadataJson) {
        AdminNotification notification = new AdminNotification();
        notification.setGameId(gameId);
        notification.setTeamId(teamId);
        notification.setSource(source);
        notification.setContent(content);
        notification.setMetadataJson(metadataJson);
        notification.setRead(false);
        return adminNotificationRepository.save(notification);
    }

    @Override
    public Optional<AdminNotification> findById(UUID notificationId) {
        return adminNotificationRepository.findById(notificationId);
    }

    @Override
    public List<AdminNotification> findByGame(UUID gameId, int page, int pageSize) {
        return adminNotificationRepository.findByGameId(gameId, page, pageSize);
    }

    @Override
    public long countByGame(UUID gameId) {
        return adminNotificationRepository.countByGameId(gameId);
    }

    @Override
    public void markAsRead(UUID notificationId, UUID adminId) {
        adminNotificationRepository.markAsRead(notificationId, adminId);
    }

    @Override
    public void markAllAsRead(UUID gameId, UUID adminId) {
        adminNotificationRepository.markAllAsReadByGame(gameId, adminId);
    }
}
