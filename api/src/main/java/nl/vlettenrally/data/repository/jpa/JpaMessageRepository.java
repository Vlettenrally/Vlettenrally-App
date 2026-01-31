package nl.vlettenrally.data.repository.jpa;

import io.quarkus.arc.profile.IfBuildProfile;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.transaction.Transactional;
import nl.vlettenrally.data.domain.Message;
import nl.vlettenrally.data.entity.jpa.AdminEntity;
import nl.vlettenrally.data.entity.jpa.GameEntity;
import nl.vlettenrally.data.entity.jpa.MessageEntity;
import nl.vlettenrally.data.entity.jpa.TeamEntity;
import nl.vlettenrally.data.enums.MessageSource;
import nl.vlettenrally.data.mapper.MessageMapper;
import nl.vlettenrally.data.repository.MessageRepository;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * JPA implementation of MessageRepository with read replica support.
 * Only active in prod profile - replaced by MockMessageRepository in dev/test.
 */
@IfBuildProfile("prod")
@ApplicationScoped
public class JpaMessageRepository extends ReadReplicaAwareRepository implements MessageRepository {

    @Override
    public Optional<Message> findById(UUID id) {
        MessageEntity entity = getReadEntityManager().find(MessageEntity.class, id);
        return Optional.ofNullable(MessageMapper.toDomain(entity));
    }

    @Override
    public List<Message> findAll() {
        List<MessageEntity> entities = getReadEntityManager()
                .createQuery("SELECT m FROM MessageEntity m ORDER BY m.sentAt DESC", MessageEntity.class)
                .getResultList();
        return entities.stream()
                .map(MessageMapper::toDomain)
                .collect(Collectors.toList());
    }

    @Override
    public List<Message> findByTeamId(UUID teamId) {
        List<MessageEntity> entities = getReadEntityManager()
                .createQuery("SELECT m FROM MessageEntity m WHERE m.team.id = :teamId ORDER BY m.sentAt DESC", MessageEntity.class)
                .setParameter("teamId", teamId)
                .getResultList();
        return entities.stream()
                .map(MessageMapper::toDomain)
                .collect(Collectors.toList());
    }

    @Override
    public List<Message> findUnreadByTeamId(UUID teamId) {
        List<MessageEntity> entities = getReadEntityManager()
                .createQuery("SELECT m FROM MessageEntity m WHERE m.team.id = :teamId AND m.read = false ORDER BY m.sentAt DESC", MessageEntity.class)
                .setParameter("teamId", teamId)
                .getResultList();
        return entities.stream()
                .map(MessageMapper::toDomain)
                .collect(Collectors.toList());
    }

    @Override
    public List<Message> findByGameId(UUID gameId) {
        List<MessageEntity> entities = getReadEntityManager()
                .createQuery("SELECT m FROM MessageEntity m WHERE m.game.id = :gameId ORDER BY m.sentAt DESC", MessageEntity.class)
                .setParameter("gameId", gameId)
                .getResultList();
        return entities.stream()
                .map(MessageMapper::toDomain)
                .collect(Collectors.toList());
    }

    @Override
    public List<Message> findByGameIdAndSource(UUID gameId, MessageSource source) {
        List<MessageEntity> entities = getReadEntityManager()
                .createQuery("SELECT m FROM MessageEntity m WHERE m.game.id = :gameId AND m.source = :source ORDER BY m.sentAt DESC", MessageEntity.class)
                .setParameter("gameId", gameId)
                .setParameter("source", source)
                .getResultList();
        return entities.stream()
                .map(MessageMapper::toDomain)
                .collect(Collectors.toList());
    }

    @Override
    public List<Message> findByGameIdAndSourcePaginated(UUID gameId, MessageSource source, int page, int pageSize) {
        List<MessageEntity> entities = getReadEntityManager()
                .createQuery("SELECT m FROM MessageEntity m WHERE m.game.id = :gameId AND m.source = :source ORDER BY m.sentAt DESC", MessageEntity.class)
                .setParameter("gameId", gameId)
                .setParameter("source", source)
                .setFirstResult(page * pageSize)
                .setMaxResults(pageSize)
                .getResultList();
        return entities.stream()
                .map(MessageMapper::toDomain)
                .collect(Collectors.toList());
    }

    @Override
    public long countByGameIdAndSource(UUID gameId, MessageSource source) {
        return getReadEntityManager()
                .createQuery("SELECT COUNT(m) FROM MessageEntity m WHERE m.game.id = :gameId AND m.source = :source", Long.class)
                .setParameter("gameId", gameId)
                .setParameter("source", source)
                .getSingleResult();
    }

    @Override
    public List<Message> findByBatchId(UUID batchId) {
        List<MessageEntity> entities = getReadEntityManager()
                .createQuery("SELECT m FROM MessageEntity m WHERE m.batchId = :batchId ORDER BY m.sentAt DESC", MessageEntity.class)
                .setParameter("batchId", batchId)
                .getResultList();
        return entities.stream()
                .map(MessageMapper::toDomain)
                .collect(Collectors.toList());
    }

    @Override
    public List<Message> findFailedByBatchId(UUID batchId) {
        List<MessageEntity> entities = getReadEntityManager()
                .createQuery("SELECT m FROM MessageEntity m WHERE m.batchId = :batchId AND m.delivered = false ORDER BY m.sentAt DESC", MessageEntity.class)
                .setParameter("batchId", batchId)
                .getResultList();
        return entities.stream()
                .map(MessageMapper::toDomain)
                .collect(Collectors.toList());
    }

    @Override
    public List<Message> findFailedByGameIdAndSource(UUID gameId, MessageSource source) {
        List<MessageEntity> entities = getReadEntityManager()
                .createQuery("SELECT m FROM MessageEntity m WHERE m.game.id = :gameId AND m.source = :source AND m.delivered = false ORDER BY m.sentAt DESC", MessageEntity.class)
                .setParameter("gameId", gameId)
                .setParameter("source", source)
                .getResultList();
        return entities.stream()
                .map(MessageMapper::toDomain)
                .collect(Collectors.toList());
    }

    @Override
    public List<Message> findDistinctBatchesByGameIdPaginated(UUID gameId, MessageSource source, int page, int pageSize) {
        // Get one representative message per batch
        List<MessageEntity> entities = getReadEntityManager()
                .createQuery("""
                    SELECT m FROM MessageEntity m 
                    WHERE m.game.id = :gameId 
                    AND m.source = :source 
                    AND m.id = (
                        SELECT MIN(m2.id) FROM MessageEntity m2 
                        WHERE m2.batchId = m.batchId OR (m2.batchId IS NULL AND m2.id = m.id)
                    )
                    ORDER BY m.sentAt DESC
                    """, MessageEntity.class)
                .setParameter("gameId", gameId)
                .setParameter("source", source)
                .setFirstResult(page * pageSize)
                .setMaxResults(pageSize)
                .getResultList();
        return entities.stream()
                .map(MessageMapper::toDomain)
                .collect(Collectors.toList());
    }

    @Override
    public long countDistinctBatchesByGameIdAndSource(UUID gameId, MessageSource source) {
        return getReadEntityManager()
                .createQuery("""
                    SELECT COUNT(DISTINCT COALESCE(m.batchId, m.id)) 
                    FROM MessageEntity m 
                    WHERE m.game.id = :gameId AND m.source = :source
                    """, Long.class)
                .setParameter("gameId", gameId)
                .setParameter("source", source)
                .getSingleResult();
    }

    @Override
    @Transactional
    public Message save(Message message) {
        MessageEntity entity = getWriteEntityManager().find(MessageEntity.class, message.getId());
        
        if (entity == null) {
            // New entity
            entity = MessageMapper.toEntity(message);
            
            // Set game relationship
            if (message.getGameId() != null) {
                GameEntity game = getWriteEntityManager().find(GameEntity.class, message.getGameId());
                entity.setGame(game);
            }
            
            // Set team relationship
            if (message.getTeamId() != null) {
                TeamEntity team = getWriteEntityManager().find(TeamEntity.class, message.getTeamId());
                entity.setTeam(team);
            }
            
            // Set sentByAdmin relationship
            if (message.getSentByAdminId() != null) {
                AdminEntity admin = getWriteEntityManager().find(AdminEntity.class, message.getSentByAdminId());
                entity.setSentByAdmin(admin);
            }
            
            getWriteEntityManager().persist(entity);
        } else {
            // Update existing
            MessageMapper.updateEntity(entity, message);
            
            // Update sentByAdmin if changed
            UUID existingSentByAdminId = entity.getSentByAdmin() != null ? entity.getSentByAdmin().getId() : null;
            if (!Objects.equals(existingSentByAdminId, message.getSentByAdminId())) {
                if (message.getSentByAdminId() != null) {
                    AdminEntity admin = getWriteEntityManager().find(AdminEntity.class, message.getSentByAdminId());
                    entity.setSentByAdmin(admin);
                } else {
                    entity.setSentByAdmin(null);
                }
            }
            
            entity = getWriteEntityManager().merge(entity);
        }
        
        return MessageMapper.toDomain(entity);
    }

    @Override
    @Transactional
    public void markAsRead(UUID id) {
        MessageEntity entity = getWriteEntityManager().find(MessageEntity.class, id);
        if (entity != null) {
            entity.setRead(true);
            getWriteEntityManager().merge(entity);
        }
    }

    @Override
    @Transactional
    public void markAllAsReadForTeam(UUID teamId) {
        getWriteEntityManager()
                .createQuery("UPDATE MessageEntity m SET m.read = true WHERE m.team.id = :teamId AND m.read = false")
                .setParameter("teamId", teamId)
                .executeUpdate();
    }

    @Override
    @Transactional
    public void delete(UUID id) {
        MessageEntity entity = getWriteEntityManager().find(MessageEntity.class, id);
        if (entity != null) {
            getWriteEntityManager().remove(entity);
        }
    }
}
