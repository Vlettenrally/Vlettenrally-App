package nl.vlettenrally.data.repository.jpa;

import io.quarkus.arc.profile.IfBuildProfile;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.persistence.NoResultException;
import jakarta.transaction.Transactional;
import nl.vlettenrally.data.domain.RefreshToken;
import nl.vlettenrally.data.entity.jpa.RefreshTokenEntity;
import nl.vlettenrally.data.mapper.RefreshTokenMapper;
import nl.vlettenrally.data.repository.RefreshTokenRepository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * JPA implementation of RefreshTokenRepository with read replica support.
 * Only active in prod profile - replaced by mock repository in dev/test.
 */
@IfBuildProfile("prod")
@ApplicationScoped
public class JpaRefreshTokenRepository extends ReadReplicaAwareRepository implements RefreshTokenRepository {

    @Override
    public Optional<RefreshToken> findById(UUID id) {
        RefreshTokenEntity entity = getReadEntityManager().find(RefreshTokenEntity.class, id);
        return Optional.ofNullable(RefreshTokenMapper.toDomain(entity));
    }

    @Override
    public Optional<RefreshToken> findByToken(String token) {
        try {
            // Primary read: refresh-token validation must see the latest revocation/rotation
            // state, otherwise a lagging replica defeats duplicate/replay detection.
            RefreshTokenEntity entity = getReadEntityManager(true)
                    .createQuery("SELECT r FROM RefreshTokenEntity r WHERE r.token = :token", RefreshTokenEntity.class)
                    .setParameter("token", token)
                    .getSingleResult();
            return Optional.ofNullable(RefreshTokenMapper.toDomain(entity));
        } catch (NoResultException e) {
            return Optional.empty();
        }
    }

    @Override
    public List<RefreshToken> findBySubject(String subject) {
        // Primary read: used to revoke all of a subject's tokens; must not miss recent ones.
        List<RefreshTokenEntity> entities = getReadEntityManager(true)
                .createQuery("SELECT r FROM RefreshTokenEntity r WHERE r.subject = :subject ORDER BY r.createdAt DESC", RefreshTokenEntity.class)
                .setParameter("subject", subject)
                .getResultList();
        return entities.stream()
                .map(RefreshTokenMapper::toDomain)
                .collect(Collectors.toList());
    }

    @Override
    public List<RefreshToken> findAll() {
        List<RefreshTokenEntity> entities = getReadEntityManager()
                .createQuery("SELECT r FROM RefreshTokenEntity r ORDER BY r.createdAt DESC", RefreshTokenEntity.class)
                .getResultList();
        return entities.stream()
                .map(RefreshTokenMapper::toDomain)
                .collect(Collectors.toList());
    }

    @Override
    @Transactional
    public RefreshToken save(RefreshToken refreshToken) {
        RefreshTokenEntity entity = getWriteEntityManager().find(RefreshTokenEntity.class, refreshToken.getId());
        
        if (entity == null) {
            // New entity
            entity = RefreshTokenMapper.toEntity(refreshToken);
            getWriteEntityManager().persist(entity);
        } else {
            // Update existing
            RefreshTokenMapper.updateEntity(entity, refreshToken);
            entity.setUpdatedAt(Instant.now());
            entity = getWriteEntityManager().merge(entity);
        }
        
        return RefreshTokenMapper.toDomain(entity);
    }

    @Override
    @Transactional
    public void delete(UUID id) {
        RefreshTokenEntity entity = getWriteEntityManager().find(RefreshTokenEntity.class, id);
        if (entity != null) {
            getWriteEntityManager().remove(entity);
        }
    }

    @Override
    @Transactional
    public void deleteByToken(String token) {
        getWriteEntityManager()
                .createQuery("DELETE FROM RefreshTokenEntity r WHERE r.token = :token")
                .setParameter("token", token)
                .executeUpdate();
    }

    @Override
    @Transactional
    public void deleteBySubject(String subject) {
        getWriteEntityManager()
                .createQuery("DELETE FROM RefreshTokenEntity r WHERE r.subject = :subject")
                .setParameter("subject", subject)
                .executeUpdate();
    }

    @Override
    @Transactional
    public void deleteExpiredTokens(Instant now) {
        getWriteEntityManager()
                .createQuery("DELETE FROM RefreshTokenEntity r WHERE r.expiresAt < :now")
                .setParameter("now", now)
                .executeUpdate();
    }
}
