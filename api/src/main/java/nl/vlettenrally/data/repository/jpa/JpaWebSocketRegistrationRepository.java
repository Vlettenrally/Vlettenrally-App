package nl.vlettenrally.data.repository.jpa;

import io.quarkus.arc.profile.IfBuildProfile;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.persistence.NoResultException;
import jakarta.transaction.Transactional;
import nl.vlettenrally.data.domain.WebSocketRegistration;
import nl.vlettenrally.data.entity.jpa.DeviceRegistrationEntity;
import nl.vlettenrally.data.entity.jpa.WebSocketRegistrationEntity;
import nl.vlettenrally.data.mapper.WebSocketRegistrationMapper;
import nl.vlettenrally.data.repository.WebSocketRegistrationRepository;

import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * JPA implementation of WebSocketRegistrationRepository with read replica support.
 * Only active in prod profile - replaced by MockWebSocketRegistrationRepository in dev/test.
 */
@IfBuildProfile("prod")
@ApplicationScoped
public class JpaWebSocketRegistrationRepository extends ReadReplicaAwareRepository implements WebSocketRegistrationRepository {

    @Override
    public Optional<WebSocketRegistration> findById(UUID id) {
        WebSocketRegistrationEntity entity = getReadEntityManager().find(WebSocketRegistrationEntity.class, id);
        return Optional.ofNullable(WebSocketRegistrationMapper.toDomain(entity));
    }

    @Override
    public List<WebSocketRegistration> findAll() {
        List<WebSocketRegistrationEntity> entities = getReadEntityManager()
                .createQuery("SELECT w FROM WebSocketRegistrationEntity w ORDER BY w.connectedAt DESC", WebSocketRegistrationEntity.class)
                .getResultList();
        return entities.stream()
                .map(WebSocketRegistrationMapper::toDomain)
                .collect(Collectors.toList());
    }

    @Override
    public Optional<WebSocketRegistration> findBySessionId(String sessionId) {
        try {
            WebSocketRegistrationEntity entity = getReadEntityManager()
                    .createQuery("SELECT w FROM WebSocketRegistrationEntity w WHERE w.sessionId = :sessionId", WebSocketRegistrationEntity.class)
                    .setParameter("sessionId", sessionId)
                    .getSingleResult();
            return Optional.ofNullable(WebSocketRegistrationMapper.toDomain(entity));
        } catch (NoResultException e) {
            return Optional.empty();
        }
    }

    @Override
    public List<WebSocketRegistration> findByDeviceRegistrationId(UUID deviceRegistrationId) {
        List<WebSocketRegistrationEntity> entities = getReadEntityManager()
                .createQuery("SELECT w FROM WebSocketRegistrationEntity w WHERE w.deviceRegistration.id = :deviceRegistrationId ORDER BY w.connectedAt DESC", WebSocketRegistrationEntity.class)
                .setParameter("deviceRegistrationId", deviceRegistrationId)
                .getResultList();
        return entities.stream()
                .map(WebSocketRegistrationMapper::toDomain)
                .collect(Collectors.toList());
    }

    @Override
    public List<WebSocketRegistration> findActiveByDeviceRegistrationId(UUID deviceRegistrationId) {
        List<WebSocketRegistrationEntity> entities = getReadEntityManager()
                .createQuery("SELECT w FROM WebSocketRegistrationEntity w WHERE w.deviceRegistration.id = :deviceRegistrationId AND w.disconnectedAt IS NULL ORDER BY w.connectedAt DESC", WebSocketRegistrationEntity.class)
                .setParameter("deviceRegistrationId", deviceRegistrationId)
                .getResultList();
        return entities.stream()
                .map(WebSocketRegistrationMapper::toDomain)
                .collect(Collectors.toList());
    }

    @Override
    @Transactional
    public WebSocketRegistration save(WebSocketRegistration webSocketRegistration) {
        WebSocketRegistrationEntity entity = getWriteEntityManager().find(WebSocketRegistrationEntity.class, webSocketRegistration.getId());
        
        if (entity == null) {
            // New entity
            entity = WebSocketRegistrationMapper.toEntity(webSocketRegistration);
            
            // Set deviceRegistration relationship
            if (webSocketRegistration.getDeviceRegistrationId() != null) {
                DeviceRegistrationEntity deviceRegistration = getWriteEntityManager().find(DeviceRegistrationEntity.class, webSocketRegistration.getDeviceRegistrationId());
                entity.setDeviceRegistration(deviceRegistration);
            }
            
            getWriteEntityManager().persist(entity);
        } else {
            // Update existing
            WebSocketRegistrationMapper.updateEntity(entity, webSocketRegistration);
            
            // Update deviceRegistration if changed
            UUID existingDeviceId = entity.getDeviceRegistration() != null ? entity.getDeviceRegistration().getId() : null;
            if (!Objects.equals(existingDeviceId, webSocketRegistration.getDeviceRegistrationId())) {
                if (webSocketRegistration.getDeviceRegistrationId() != null) {
                    DeviceRegistrationEntity deviceRegistration = getWriteEntityManager().find(DeviceRegistrationEntity.class, webSocketRegistration.getDeviceRegistrationId());
                    entity.setDeviceRegistration(deviceRegistration);
                } else {
                    entity.setDeviceRegistration(null);
                }
            }
            
            entity = getWriteEntityManager().merge(entity);
        }
        
        return WebSocketRegistrationMapper.toDomain(entity);
    }

    @Override
    @Transactional
    public void markDisconnected(String sessionId) {
        getWriteEntityManager()
                .createQuery("UPDATE WebSocketRegistrationEntity w SET w.disconnectedAt = :now WHERE w.sessionId = :sessionId AND w.disconnectedAt IS NULL")
                .setParameter("now", Instant.now())
                .setParameter("sessionId", sessionId)
                .executeUpdate();
    }

    @Override
    @Transactional
    public void delete(UUID id) {
        WebSocketRegistrationEntity entity = getWriteEntityManager().find(WebSocketRegistrationEntity.class, id);
        if (entity != null) {
            getWriteEntityManager().remove(entity);
        }
    }
}
