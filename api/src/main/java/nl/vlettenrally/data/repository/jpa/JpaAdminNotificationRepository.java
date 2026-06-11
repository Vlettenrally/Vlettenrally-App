package nl.vlettenrally.data.repository.jpa;

import io.quarkus.arc.profile.IfBuildProfile;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.transaction.Transactional;
import nl.vlettenrally.data.domain.AdminNotification;
import nl.vlettenrally.data.entity.jpa.AdminEntity;
import nl.vlettenrally.data.entity.jpa.AdminNotificationEntity;
import nl.vlettenrally.data.entity.jpa.GameEntity;
import nl.vlettenrally.data.entity.jpa.TeamEntity;
import nl.vlettenrally.data.mapper.AdminNotificationMapper;
import nl.vlettenrally.data.repository.AdminNotificationRepository;

import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

@IfBuildProfile("prod")
@ApplicationScoped
public class JpaAdminNotificationRepository extends ReadReplicaAwareRepository implements AdminNotificationRepository {

    @Override
    public Optional<AdminNotification> findById(UUID id) {
        AdminNotificationEntity entity = getReadEntityManager().find(AdminNotificationEntity.class, id);
        return Optional.ofNullable(AdminNotificationMapper.toDomain(entity));
    }

    @Override
    public List<AdminNotification> findByGameId(UUID gameId, int page, int pageSize) {
        List<AdminNotificationEntity> entities = getReadEntityManager()
            .createQuery("""
                SELECT n FROM AdminNotificationEntity n
                WHERE n.game.id = :gameId
                ORDER BY n.createdAt DESC
                """, AdminNotificationEntity.class)
            .setParameter("gameId", gameId)
            .setFirstResult(page * pageSize)
            .setMaxResults(pageSize)
            .getResultList();
        return entities.stream().map(AdminNotificationMapper::toDomain).collect(Collectors.toList());
    }

    @Override
    public long countByGameId(UUID gameId) {
        return getReadEntityManager()
            .createQuery("SELECT COUNT(n) FROM AdminNotificationEntity n WHERE n.game.id = :gameId", Long.class)
            .setParameter("gameId", gameId)
            .getSingleResult();
    }

    @Override
    @Transactional
    public AdminNotification save(AdminNotification notification) {
        AdminNotificationEntity entity = getWriteEntityManager().find(AdminNotificationEntity.class, notification.getId());
        if (entity == null) {
            entity = AdminNotificationMapper.toEntity(notification);
            if (notification.getGameId() != null) {
                GameEntity game = getWriteEntityManager().find(GameEntity.class, notification.getGameId());
                entity.setGame(game);
            }
            if (notification.getTeamId() != null) {
                TeamEntity team = getWriteEntityManager().find(TeamEntity.class, notification.getTeamId());
                entity.setTeam(team);
            }
            if (notification.getReadByAdminId() != null) {
                AdminEntity admin = getWriteEntityManager().find(AdminEntity.class, notification.getReadByAdminId());
                entity.setReadByAdmin(admin);
            }
            getWriteEntityManager().persist(entity);
            return AdminNotificationMapper.toDomain(entity);
        }

        AdminNotificationMapper.updateEntity(entity, notification);
        UUID existingReadBy = entity.getReadByAdmin() != null ? entity.getReadByAdmin().getId() : null;
        if (!Objects.equals(existingReadBy, notification.getReadByAdminId())) {
            if (notification.getReadByAdminId() == null) {
                entity.setReadByAdmin(null);
            } else {
                AdminEntity admin = getWriteEntityManager().find(AdminEntity.class, notification.getReadByAdminId());
                entity.setReadByAdmin(admin);
            }
        }
        entity = getWriteEntityManager().merge(entity);
        return AdminNotificationMapper.toDomain(entity);
    }

    @Override
    @Transactional
    public void markAsRead(UUID notificationId, UUID readByAdminId) {
        AdminNotificationEntity entity = getWriteEntityManager().find(AdminNotificationEntity.class, notificationId);
        if (entity == null || entity.isRead()) {
            return;
        }
        entity.setRead(true);
        entity.setReadAt(Instant.now());
        if (readByAdminId != null) {
            AdminEntity admin = getWriteEntityManager().find(AdminEntity.class, readByAdminId);
            entity.setReadByAdmin(admin);
        }
        getWriteEntityManager().merge(entity);
    }

    @Override
    @Transactional
    public void markAllAsReadByGame(UUID gameId, UUID readByAdminId) {
        List<AdminNotificationEntity> entities = getWriteEntityManager()
            .createQuery("""
                SELECT n FROM AdminNotificationEntity n
                WHERE n.game.id = :gameId AND n.read = false
                """, AdminNotificationEntity.class)
            .setParameter("gameId", gameId)
            .getResultList();

        AdminEntity admin = null;
        if (readByAdminId != null) {
            admin = getWriteEntityManager().find(AdminEntity.class, readByAdminId);
        }
        Instant now = Instant.now();
        for (AdminNotificationEntity entity : entities) {
            entity.setRead(true);
            entity.setReadAt(now);
            entity.setReadByAdmin(admin);
            getWriteEntityManager().merge(entity);
        }
    }
}
