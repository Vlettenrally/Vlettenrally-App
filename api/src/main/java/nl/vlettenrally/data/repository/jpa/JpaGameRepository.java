package nl.vlettenrally.data.repository.jpa;

import io.quarkus.arc.profile.IfBuildProfile;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.transaction.Transactional;
import nl.vlettenrally.data.domain.Game;
import nl.vlettenrally.data.entity.jpa.GameEntity;
import nl.vlettenrally.data.mapper.GameMapper;
import nl.vlettenrally.data.repository.GameRepository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * JPA implementation of GameRepository with read replica support.
 * Only active in prod profile - replaced by JpaGameRepository in dev/test.
 */
@IfBuildProfile("prod")
@ApplicationScoped
public class JpaGameRepository extends ReadReplicaAwareRepository implements GameRepository {

    @Override
    public Optional<Game> findById(UUID id) {
        GameEntity entity = getReadEntityManager().find(GameEntity.class, id);
        return Optional.ofNullable(GameMapper.toDomain(entity));
    }

    @Override
    public List<Game> findAll() {
        List<GameEntity> entities = getReadEntityManager()
                .createQuery("SELECT g FROM GameEntity g ORDER BY g.startTime DESC", GameEntity.class)
                .getResultList();
        return entities.stream()
                .map(GameMapper::toDomain)
                .collect(Collectors.toList());
    }

    @Override
    public List<Game> findByStatus(Game.GameStatus status) {
        GameEntity.GameStatus jpaStatus = mapStatus(status);
        List<GameEntity> entities = getReadEntityManager()
                .createQuery("SELECT g FROM GameEntity g WHERE g.status = :status ORDER BY g.startTime DESC", GameEntity.class)
                .setParameter("status", jpaStatus)
                .getResultList();
        return entities.stream()
                .map(GameMapper::toDomain)
                .collect(Collectors.toList());
    }

    @Override
    public List<Game> findActiveGames() {
        List<GameEntity> entities = getReadEntityManager()
                .createQuery("SELECT g FROM GameEntity g WHERE g.status = :status ORDER BY g.startTime DESC", GameEntity.class)
                .setParameter("status", GameEntity.GameStatus.ACTIVE)
                .getResultList();
        return entities.stream()
                .map(GameMapper::toDomain)
                .collect(Collectors.toList());
    }

    @Override
    public int count() {
        Long count = getReadEntityManager()
                .createQuery("SELECT COUNT(g) FROM GameEntity g", Long.class)
                .getSingleResult();
        return count.intValue();
    }

    @Override
    @Transactional
    public Game save(Game game) {
        GameEntity entity = getWriteEntityManager().find(GameEntity.class, game.getId());
        
        if (entity == null) {
            // New entity
            entity = GameMapper.toEntity(game);
            getWriteEntityManager().persist(entity);
        } else {
            // Update existing
            GameMapper.updateEntity(entity, game);
            entity.setUpdatedAt(Instant.now());
            entity = getWriteEntityManager().merge(entity);
        }
        
        return GameMapper.toDomain(entity);
    }

    @Override
    @Transactional
    public void delete(UUID id) {
        GameEntity entity = getWriteEntityManager().find(GameEntity.class, id);
        if (entity != null) {
            getWriteEntityManager().remove(entity);
        }
    }

    private GameEntity.GameStatus mapStatus(Game.GameStatus status) {
        if (status == null) {
            return null;
        }
        return switch (status) {
            case PLANNED -> GameEntity.GameStatus.PLANNED;
            case ACTIVE -> GameEntity.GameStatus.ACTIVE;
            case COMPLETED -> GameEntity.GameStatus.COMPLETED;
        };
    }
}
