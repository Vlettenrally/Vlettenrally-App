package nl.vlettenrally.data.mapper;

import nl.vlettenrally.data.domain.AdminNotification;
import nl.vlettenrally.data.entity.jpa.AdminNotificationEntity;

public final class AdminNotificationMapper {

    private AdminNotificationMapper() {
    }

    public static AdminNotification toDomain(AdminNotificationEntity entity) {
        if (entity == null) {
            return null;
        }

        AdminNotification domain = new AdminNotification();
        domain.setId(entity.getId());
        domain.setGameId(entity.getGame() != null ? entity.getGame().getId() : null);
        domain.setTeamId(entity.getTeam() != null ? entity.getTeam().getId() : null);
        domain.setSource(entity.getSource());
        domain.setContent(entity.getContent());
        domain.setMetadataJson(entity.getMetadataJson());
        domain.setCreatedAt(entity.getCreatedAt());
        domain.setRead(entity.isRead());
        domain.setReadAt(entity.getReadAt());
        domain.setReadByAdminId(entity.getReadByAdmin() != null ? entity.getReadByAdmin().getId() : null);
        return domain;
    }

    public static AdminNotificationEntity toEntity(AdminNotification domain) {
        if (domain == null) {
            return null;
        }
        AdminNotificationEntity entity = new AdminNotificationEntity();
        entity.setId(domain.getId());
        entity.setSource(domain.getSource());
        entity.setContent(domain.getContent());
        entity.setMetadataJson(domain.getMetadataJson());
        entity.setCreatedAt(domain.getCreatedAt());
        entity.setRead(domain.isRead());
        entity.setReadAt(domain.getReadAt());
        return entity;
    }

    public static void updateEntity(AdminNotificationEntity entity, AdminNotification domain) {
        if (entity == null || domain == null) {
            return;
        }
        entity.setSource(domain.getSource());
        entity.setContent(domain.getContent());
        entity.setMetadataJson(domain.getMetadataJson());
        entity.setRead(domain.isRead());
        entity.setReadAt(domain.getReadAt());
    }
}
