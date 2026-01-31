package nl.vlettenrally.data.repository.jpa;

import io.quarkus.arc.profile.IfBuildProfile;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.persistence.NoResultException;
import jakarta.transaction.Transactional;
import nl.vlettenrally.data.domain.FcmToken;
import nl.vlettenrally.data.entity.jpa.DeviceRegistrationEntity;
import nl.vlettenrally.data.entity.jpa.FcmTokenEntity;
import nl.vlettenrally.data.mapper.FcmTokenMapper;
import nl.vlettenrally.data.repository.FcmTokenRepository;

import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * JPA implementation of FcmTokenRepository with read replica support.
 * Only active in prod profile - replaced by MockFcmTokenRepository in dev/test.
 */
@IfBuildProfile("prod")
@ApplicationScoped
public class JpaFcmTokenRepository extends ReadReplicaAwareRepository implements FcmTokenRepository {

    @Override
    public Optional<FcmToken> findById(UUID id) {
        FcmTokenEntity entity = getReadEntityManager().find(FcmTokenEntity.class, id);
        return Optional.ofNullable(FcmTokenMapper.toDomain(entity));
    }

    @Override
    public List<FcmToken> findAll() {
        List<FcmTokenEntity> entities = getReadEntityManager()
                .createQuery("SELECT f FROM FcmTokenEntity f ORDER BY f.updatedAt DESC", FcmTokenEntity.class)
                .getResultList();
        return entities.stream()
                .map(FcmTokenMapper::toDomain)
                .collect(Collectors.toList());
    }

    @Override
    public Optional<FcmToken> findByDeviceRegistrationId(UUID deviceRegistrationId) {
        try {
            FcmTokenEntity entity = getReadEntityManager()
                    .createQuery("SELECT f FROM FcmTokenEntity f WHERE f.deviceRegistration.id = :deviceRegistrationId", FcmTokenEntity.class)
                    .setParameter("deviceRegistrationId", deviceRegistrationId)
                    .getSingleResult();
            return Optional.ofNullable(FcmTokenMapper.toDomain(entity));
        } catch (NoResultException e) {
            return Optional.empty();
        }
    }

    @Override
    public List<FcmToken> findByTeamId(UUID teamId) {
        List<FcmTokenEntity> entities = getReadEntityManager()
                .createQuery("SELECT f FROM FcmTokenEntity f WHERE f.deviceRegistration.team.id = :teamId ORDER BY f.updatedAt DESC", FcmTokenEntity.class)
                .setParameter("teamId", teamId)
                .getResultList();
        return entities.stream()
                .map(FcmTokenMapper::toDomain)
                .collect(Collectors.toList());
    }

    @Override
    @Transactional
    public FcmToken save(FcmToken fcmToken) {
        FcmTokenEntity entity = getWriteEntityManager().find(FcmTokenEntity.class, fcmToken.getId());
        
        if (entity == null) {
            // New entity
            entity = FcmTokenMapper.toEntity(fcmToken);
            
            // Set deviceRegistration relationship
            if (fcmToken.getDeviceRegistrationId() != null) {
                DeviceRegistrationEntity deviceRegistration = getWriteEntityManager().find(DeviceRegistrationEntity.class, fcmToken.getDeviceRegistrationId());
                entity.setDeviceRegistration(deviceRegistration);
            }
            
            getWriteEntityManager().persist(entity);
        } else {
            // Update existing
            FcmTokenMapper.updateEntity(entity, fcmToken);
            entity.setUpdatedAt(Instant.now());
            
            // Update deviceRegistration if changed
            UUID existingDeviceId = entity.getDeviceRegistration() != null ? entity.getDeviceRegistration().getId() : null;
            if (!Objects.equals(existingDeviceId, fcmToken.getDeviceRegistrationId())) {
                if (fcmToken.getDeviceRegistrationId() != null) {
                    DeviceRegistrationEntity deviceRegistration = getWriteEntityManager().find(DeviceRegistrationEntity.class, fcmToken.getDeviceRegistrationId());
                    entity.setDeviceRegistration(deviceRegistration);
                } else {
                    entity.setDeviceRegistration(null);
                }
            }
            
            entity = getWriteEntityManager().merge(entity);
        }
        
        return FcmTokenMapper.toDomain(entity);
    }

    @Override
    @Transactional
    public void delete(UUID id) {
        FcmTokenEntity entity = getWriteEntityManager().find(FcmTokenEntity.class, id);
        if (entity != null) {
            getWriteEntityManager().remove(entity);
        }
    }

    @Override
    @Transactional
    public void deleteByDeviceRegistrationId(UUID deviceRegistrationId) {
        getWriteEntityManager()
                .createQuery("DELETE FROM FcmTokenEntity f WHERE f.deviceRegistration.id = :deviceRegistrationId")
                .setParameter("deviceRegistrationId", deviceRegistrationId)
                .executeUpdate();
    }
}
