package nl.vlettenrally.data.repository.jpa;

import io.quarkus.arc.profile.IfBuildProfile;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.transaction.Transactional;
import nl.vlettenrally.data.domain.Checkpoint;
import nl.vlettenrally.data.entity.jpa.CheckpointEntity;
import nl.vlettenrally.data.entity.jpa.GameEntity;
import nl.vlettenrally.data.mapper.CheckpointMapper;
import nl.vlettenrally.data.repository.CheckpointRepository;

import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * JPA implementation of CheckpointRepository with read replica support.
 * Only active in prod profile - replaced by MockCheckpointRepository in dev/test.
 */
@IfBuildProfile("prod")
@ApplicationScoped
public class JpaCheckpointRepository extends ReadReplicaAwareRepository implements CheckpointRepository {

    @Override
    public Optional<Checkpoint> findById(UUID id) {
        CheckpointEntity entity = getReadEntityManager().find(CheckpointEntity.class, id);
        return Optional.ofNullable(CheckpointMapper.toDomain(entity));
    }

    @Override
    public List<Checkpoint> findAll() {
        List<CheckpointEntity> entities = getReadEntityManager()
                .createQuery("SELECT c FROM CheckpointEntity c ORDER BY c.openingTime", CheckpointEntity.class)
                .getResultList();
        return entities.stream()
                .map(CheckpointMapper::toDomain)
                .collect(Collectors.toList());
    }

    @Override
    public List<Checkpoint> findByGameId(UUID gameId) {
        List<CheckpointEntity> entities = getReadEntityManager()
                .createQuery("SELECT c FROM CheckpointEntity c WHERE c.game.id = :gameId ORDER BY c.openingTime", CheckpointEntity.class)
                .setParameter("gameId", gameId)
                .getResultList();
        return entities.stream()
                .map(CheckpointMapper::toDomain)
                .collect(Collectors.toList());
    }

    @Override
    public Optional<Checkpoint> findByGameIdAndExternalId(UUID gameId, String externalId) {
        try {
            CheckpointEntity entity = getReadEntityManager()
                    .createQuery("SELECT c FROM CheckpointEntity c WHERE c.game.id = :gameId AND c.externalId = :externalId", CheckpointEntity.class)
                    .setParameter("gameId", gameId)
                    .setParameter("externalId", externalId)
                    .getSingleResult();
            return Optional.ofNullable(CheckpointMapper.toDomain(entity));
        } catch (jakarta.persistence.NoResultException e) {
            return Optional.empty();
        }
    }

    @Override
    @Transactional
    public Checkpoint save(Checkpoint checkpoint) {
        CheckpointEntity entity = getWriteEntityManager().find(CheckpointEntity.class, checkpoint.getId());
        
        if (entity == null) {
            // New entity
            entity = CheckpointMapper.toEntity(checkpoint);
            
            // Set game relationship
            if (checkpoint.getGameId() != null) {
                GameEntity game = getWriteEntityManager().find(GameEntity.class, checkpoint.getGameId());
                entity.setGame(game);
            }
            
            getWriteEntityManager().persist(entity);
        } else {
            // Update existing
            CheckpointMapper.updateEntity(entity, checkpoint);
            entity.setUpdatedAt(Instant.now());
            
            // Update game if changed
            UUID existingGameId = entity.getGame() != null ? entity.getGame().getId() : null;
            if (!Objects.equals(existingGameId, checkpoint.getGameId())) {
                if (checkpoint.getGameId() != null) {
                    GameEntity game = getWriteEntityManager().find(GameEntity.class, checkpoint.getGameId());
                    entity.setGame(game);
                } else {
                    entity.setGame(null);
                }
            }
            
            entity = getWriteEntityManager().merge(entity);
        }
        
        return CheckpointMapper.toDomain(entity);
    }

    @Override
    @Transactional
    public void delete(UUID id) {
        CheckpointEntity entity = getWriteEntityManager().find(CheckpointEntity.class, id);
        if (entity != null) {
            getWriteEntityManager().remove(entity);
        }
    }
}
