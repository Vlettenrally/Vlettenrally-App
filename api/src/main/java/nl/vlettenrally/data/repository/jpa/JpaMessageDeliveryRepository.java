package nl.vlettenrally.data.repository.jpa;

import io.quarkus.arc.profile.IfBuildProfile;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.persistence.NoResultException;
import jakarta.transaction.Transactional;
import nl.vlettenrally.data.domain.MessageDelivery;
import nl.vlettenrally.data.entity.jpa.MessageDeliveryEntity;
import nl.vlettenrally.data.entity.jpa.MessageEntity;
import nl.vlettenrally.data.entity.jpa.TeamEntity;
import nl.vlettenrally.data.mapper.MessageDeliveryMapper;
import nl.vlettenrally.data.repository.MessageDeliveryRepository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * JPA implementation of MessageDeliveryRepository with read replica support.
 * Only active in prod profile - replaced by MockMessageDeliveryRepository in dev/test.
 */
@IfBuildProfile("prod")
@ApplicationScoped
public class JpaMessageDeliveryRepository extends ReadReplicaAwareRepository implements MessageDeliveryRepository {

    @Override
    public Optional<MessageDelivery> findById(UUID id) {
        MessageDeliveryEntity entity = getReadEntityManager().find(MessageDeliveryEntity.class, id);
        return Optional.ofNullable(MessageDeliveryMapper.toDomain(entity));
    }

    @Override
    public List<MessageDelivery> findByMessageId(UUID messageId) {
        List<MessageDeliveryEntity> entities = getReadEntityManager()
                .createQuery("SELECT d FROM MessageDeliveryEntity d WHERE d.message.id = :messageId ORDER BY d.createdAt DESC", MessageDeliveryEntity.class)
                .setParameter("messageId", messageId)
                .getResultList();
        return entities.stream()
                .map(MessageDeliveryMapper::toDomain)
                .collect(Collectors.toList());
    }

    @Override
    public List<MessageDelivery> findByTeamId(UUID teamId) {
        List<MessageDeliveryEntity> entities = getReadEntityManager()
                .createQuery("SELECT d FROM MessageDeliveryEntity d WHERE d.team.id = :teamId ORDER BY d.createdAt DESC", MessageDeliveryEntity.class)
                .setParameter("teamId", teamId)
                .getResultList();
        return entities.stream()
                .map(MessageDeliveryMapper::toDomain)
                .collect(Collectors.toList());
    }

    @Override
    public Optional<MessageDelivery> findByMessageIdAndTeamId(UUID messageId, UUID teamId) {
        try {
            MessageDeliveryEntity entity = getReadEntityManager()
                    .createQuery("SELECT d FROM MessageDeliveryEntity d WHERE d.message.id = :messageId AND d.team.id = :teamId", MessageDeliveryEntity.class)
                    .setParameter("messageId", messageId)
                    .setParameter("teamId", teamId)
                    .getSingleResult();
            return Optional.ofNullable(MessageDeliveryMapper.toDomain(entity));
        } catch (NoResultException e) {
            return Optional.empty();
        }
    }

    @Override
    public List<MessageDelivery> findFailedByMessageId(UUID messageId) {
        List<MessageDeliveryEntity> entities = getReadEntityManager()
                .createQuery("SELECT d FROM MessageDeliveryEntity d WHERE d.message.id = :messageId AND d.delivered = false ORDER BY d.createdAt DESC", MessageDeliveryEntity.class)
                .setParameter("messageId", messageId)
                .getResultList();
        return entities.stream()
                .map(MessageDeliveryMapper::toDomain)
                .collect(Collectors.toList());
    }

    @Override
    public List<MessageDelivery> findFailedByGameId(UUID gameId) {
        List<MessageDeliveryEntity> entities = getReadEntityManager()
                .createQuery("SELECT d FROM MessageDeliveryEntity d WHERE d.message.game.id = :gameId AND d.delivered = false ORDER BY d.createdAt DESC", MessageDeliveryEntity.class)
                .setParameter("gameId", gameId)
                .getResultList();
        return entities.stream()
                .map(MessageDeliveryMapper::toDomain)
                .collect(Collectors.toList());
    }

    @Override
    @Transactional
    public MessageDelivery save(MessageDelivery delivery) {
        MessageDeliveryEntity entity = getWriteEntityManager().find(MessageDeliveryEntity.class, delivery.getId());
        
        if (entity == null) {
            // New entity
            entity = MessageDeliveryMapper.toEntity(delivery);
            
            // Set message relationship
            if (delivery.getMessageId() != null) {
                MessageEntity message = getWriteEntityManager().find(MessageEntity.class, delivery.getMessageId());
                entity.setMessage(message);
            }
            
            // Set team relationship
            if (delivery.getTeamId() != null) {
                TeamEntity team = getWriteEntityManager().find(TeamEntity.class, delivery.getTeamId());
                entity.setTeam(team);
            }
            
            getWriteEntityManager().persist(entity);
        } else {
            // Update existing
            MessageDeliveryMapper.updateEntity(entity, delivery);
            entity.setUpdatedAt(Instant.now());
            entity = getWriteEntityManager().merge(entity);
        }
        
        return MessageDeliveryMapper.toDomain(entity);
    }

    @Override
    @Transactional
    public void delete(UUID id) {
        MessageDeliveryEntity entity = getWriteEntityManager().find(MessageDeliveryEntity.class, id);
        if (entity != null) {
            getWriteEntityManager().remove(entity);
        }
    }
}
