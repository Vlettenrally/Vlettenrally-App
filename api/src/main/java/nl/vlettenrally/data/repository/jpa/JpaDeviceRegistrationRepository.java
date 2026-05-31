package nl.vlettenrally.data.repository.jpa;

import io.quarkus.arc.profile.IfBuildProfile;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.persistence.NoResultException;
import jakarta.transaction.Transactional;
import nl.vlettenrally.data.domain.DeviceRegistration;
import nl.vlettenrally.data.entity.jpa.AdminEntity;
import nl.vlettenrally.data.entity.jpa.DeviceRegistrationEntity;
import nl.vlettenrally.data.entity.jpa.TeamEntity;
import nl.vlettenrally.data.mapper.DeviceRegistrationMapper;
import nl.vlettenrally.data.repository.DeviceRegistrationRepository;

import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * JPA implementation of DeviceRegistrationRepository with read replica support.
 * Only active in prod profile - replaced by MockDeviceRegistrationRepository in dev/test.
 */
@IfBuildProfile("prod")
@ApplicationScoped
public class JpaDeviceRegistrationRepository extends ReadReplicaAwareRepository implements DeviceRegistrationRepository {

    @Override
    public Optional<DeviceRegistration> findById(UUID id) {
        DeviceRegistrationEntity entity = getReadEntityManager().find(DeviceRegistrationEntity.class, id);
        return Optional.ofNullable(DeviceRegistrationMapper.toDomain(entity));
    }

    @Override
    public List<DeviceRegistration> findAll() {
        List<DeviceRegistrationEntity> entities = getReadEntityManager()
                .createQuery("SELECT d FROM DeviceRegistrationEntity d ORDER BY d.lastSeen DESC", DeviceRegistrationEntity.class)
                .getResultList();
        return entities.stream()
                .map(DeviceRegistrationMapper::toDomain)
                .collect(Collectors.toList());
    }

    @Override
    public Optional<DeviceRegistration> findByTeamId(UUID teamId) {
        List<DeviceRegistrationEntity> entities = getReadEntityManager()
                .createQuery("SELECT d FROM DeviceRegistrationEntity d WHERE d.team.id = :teamId ORDER BY d.createdAt ASC", DeviceRegistrationEntity.class)
                .setParameter("teamId", teamId)
                .setMaxResults(1)
                .getResultList();
        if (entities.isEmpty()) {
            return Optional.empty();
        }
        return Optional.ofNullable(DeviceRegistrationMapper.toDomain(entities.getFirst()));
    }

    @Override
    public List<DeviceRegistration> findAllByTeamId(UUID teamId) {
        List<DeviceRegistrationEntity> entities = getReadEntityManager()
                .createQuery("SELECT d FROM DeviceRegistrationEntity d WHERE d.team.id = :teamId ORDER BY d.createdAt ASC", DeviceRegistrationEntity.class)
                .setParameter("teamId", teamId)
                .getResultList();
        return entities.stream()
                .map(DeviceRegistrationMapper::toDomain)
                .collect(Collectors.toList());
    }

    @Override
    public Optional<DeviceRegistration> findByTeamIdAndId(UUID teamId, UUID deviceId) {
        try {
            DeviceRegistrationEntity entity = getReadEntityManager()
                    .createQuery("SELECT d FROM DeviceRegistrationEntity d WHERE d.team.id = :teamId AND d.id = :deviceId", DeviceRegistrationEntity.class)
                    .setParameter("teamId", teamId)
                    .setParameter("deviceId", deviceId)
                    .getSingleResult();
            return Optional.ofNullable(DeviceRegistrationMapper.toDomain(entity));
        } catch (NoResultException e) {
            return Optional.empty();
        }
    }

    @Override
    @Transactional
    public void deleteByTeamId(UUID teamId) {
        getWriteEntityManager()
                .createQuery("DELETE FROM DeviceRegistrationEntity d WHERE d.team.id = :teamId")
                .setParameter("teamId", teamId)
                .executeUpdate();
    }

    @Override
    public Optional<DeviceRegistration> findByAdminId(UUID adminId) {
        List<DeviceRegistrationEntity> entities = getReadEntityManager()
                .createQuery("SELECT d FROM DeviceRegistrationEntity d WHERE d.admin.id = :adminId ORDER BY d.createdAt ASC", DeviceRegistrationEntity.class)
                .setParameter("adminId", adminId)
                .setMaxResults(1)
                .getResultList();
        if (entities.isEmpty()) {
            return Optional.empty();
        }
        return Optional.ofNullable(DeviceRegistrationMapper.toDomain(entities.getFirst()));
    }

    @Override
    public List<DeviceRegistration> findAllByAdminId(UUID adminId) {
        List<DeviceRegistrationEntity> entities = getReadEntityManager()
                .createQuery("SELECT d FROM DeviceRegistrationEntity d WHERE d.admin.id = :adminId ORDER BY d.createdAt ASC", DeviceRegistrationEntity.class)
                .setParameter("adminId", adminId)
                .getResultList();
        return entities.stream()
                .map(DeviceRegistrationMapper::toDomain)
                .collect(Collectors.toList());
    }

    @Override
    public Optional<DeviceRegistration> findByAdminIdAndId(UUID adminId, UUID deviceId) {
        try {
            DeviceRegistrationEntity entity = getReadEntityManager()
                    .createQuery("SELECT d FROM DeviceRegistrationEntity d WHERE d.admin.id = :adminId AND d.id = :deviceId", DeviceRegistrationEntity.class)
                    .setParameter("adminId", adminId)
                    .setParameter("deviceId", deviceId)
                    .getSingleResult();
            return Optional.ofNullable(DeviceRegistrationMapper.toDomain(entity));
        } catch (NoResultException e) {
            return Optional.empty();
        }
    }

    @Override
    @Transactional
    public DeviceRegistration save(DeviceRegistration deviceRegistration) {
        DeviceRegistrationEntity entity = getWriteEntityManager().find(DeviceRegistrationEntity.class, deviceRegistration.getId());
        
        if (entity == null) {
            // New entity
            entity = DeviceRegistrationMapper.toEntity(deviceRegistration);
            
            // Set team relationship
            if (deviceRegistration.getTeamId() != null) {
                TeamEntity team = getWriteEntityManager().find(TeamEntity.class, deviceRegistration.getTeamId());
                entity.setTeam(team);
            }
            
            // Set admin relationship
            if (deviceRegistration.getAdminId() != null) {
                AdminEntity admin = getWriteEntityManager().find(AdminEntity.class, deviceRegistration.getAdminId());
                entity.setAdmin(admin);
            }
            
            getWriteEntityManager().persist(entity);
        } else {
            // Update existing
            DeviceRegistrationMapper.updateEntity(entity, deviceRegistration);
            
            // Update team if changed
            UUID existingTeamId = entity.getTeam() != null ? entity.getTeam().getId() : null;
            if (!Objects.equals(existingTeamId, deviceRegistration.getTeamId())) {
                if (deviceRegistration.getTeamId() != null) {
                    TeamEntity team = getWriteEntityManager().find(TeamEntity.class, deviceRegistration.getTeamId());
                    entity.setTeam(team);
                } else {
                    entity.setTeam(null);
                }
            }
            
            // Update admin if changed
            UUID existingAdminId = entity.getAdmin() != null ? entity.getAdmin().getId() : null;
            if (!Objects.equals(existingAdminId, deviceRegistration.getAdminId())) {
                if (deviceRegistration.getAdminId() != null) {
                    AdminEntity admin = getWriteEntityManager().find(AdminEntity.class, deviceRegistration.getAdminId());
                    entity.setAdmin(admin);
                } else {
                    entity.setAdmin(null);
                }
            }
            
            entity = getWriteEntityManager().merge(entity);
        }
        
        return DeviceRegistrationMapper.toDomain(entity);
    }

    @Override
    @Transactional
    public void updateLastSeen(UUID id) {
        DeviceRegistrationEntity entity = getWriteEntityManager().find(DeviceRegistrationEntity.class, id);
        if (entity != null) {
            entity.setLastSeen(Instant.now());
            getWriteEntityManager().merge(entity);
        }
    }

    @Override
    @Transactional
    public void delete(UUID id) {
        DeviceRegistrationEntity entity = getWriteEntityManager().find(DeviceRegistrationEntity.class, id);
        if (entity != null) {
            getWriteEntityManager().remove(entity);
        }
    }
}
