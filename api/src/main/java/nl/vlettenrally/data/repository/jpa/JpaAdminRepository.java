package nl.vlettenrally.data.repository.jpa;

import io.quarkus.arc.profile.IfBuildProfile;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.transaction.Transactional;
import nl.vlettenrally.data.domain.Admin;
import nl.vlettenrally.data.entity.jpa.AdminEntity;
import nl.vlettenrally.data.mapper.AdminMapper;
import nl.vlettenrally.data.repository.AdminRepository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * JPA implementation of AdminRepository with read replica support.
 * Only active in prod profile - replaced by MockAdminRepository in dev/test.
 */
@IfBuildProfile("prod")
@ApplicationScoped
public class JpaAdminRepository extends ReadReplicaAwareRepository implements AdminRepository {

    @Override
    public Optional<Admin> findById(UUID id) {
        AdminEntity entity = getReadEntityManager().find(AdminEntity.class, id);
        return Optional.ofNullable(AdminMapper.toDomain(entity));
    }

    @Override
    public Optional<Admin> findByUsername(String username) {
        List<AdminEntity> entities = getReadEntityManager()
                .createQuery("SELECT a FROM AdminEntity a WHERE a.username = :username", AdminEntity.class)
                .setParameter("username", username)
                .getResultList();
        return entities.stream()
                .findFirst()
                .map(AdminMapper::toDomain);
    }

    @Override
    public List<Admin> findAll() {
        List<AdminEntity> entities = getReadEntityManager()
                .createQuery("SELECT a FROM AdminEntity a ORDER BY a.username", AdminEntity.class)
                .getResultList();
        return entities.stream()
                .map(AdminMapper::toDomain)
                .collect(Collectors.toList());
    }

    @Override
    @Transactional
    public Admin save(Admin admin) {
        AdminEntity entity = getWriteEntityManager().find(AdminEntity.class, admin.getId());
        
        if (entity == null) {
            // New entity
            entity = AdminMapper.toEntity(admin);
            getWriteEntityManager().persist(entity);
        } else {
            // Update existing
            AdminMapper.updateEntity(entity, admin);
            entity.setUpdatedAt(Instant.now());
            entity = getWriteEntityManager().merge(entity);
        }
        
        return AdminMapper.toDomain(entity);
    }

    @Override
    @Transactional
    public void delete(UUID id) {
        AdminEntity entity = getWriteEntityManager().find(AdminEntity.class, id);
        if (entity != null) {
            getWriteEntityManager().remove(entity);
        }
    }
}
