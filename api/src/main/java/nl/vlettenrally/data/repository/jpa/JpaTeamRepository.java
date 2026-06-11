package nl.vlettenrally.data.repository.jpa;

import io.quarkus.arc.profile.IfBuildProfile;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.persistence.NoResultException;
import jakarta.transaction.Transactional;
import nl.vlettenrally.data.domain.Team;
import nl.vlettenrally.data.entity.jpa.DeviceRegistrationEntity;
import nl.vlettenrally.data.entity.jpa.GameEntity;
import nl.vlettenrally.data.entity.jpa.TeamEntity;
import nl.vlettenrally.data.mapper.TeamMapper;
import nl.vlettenrally.data.repository.TeamRepository;

import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;

/**
 * JPA implementation of TeamRepository with read replica support.
 * Only active in prod profile - replaced by MockTeamRepository in dev/test.
 */
@IfBuildProfile("prod")
@ApplicationScoped
public class JpaTeamRepository extends ReadReplicaAwareRepository implements TeamRepository {

    @Override
    public Optional<Team> findById(UUID id) {
        TeamEntity entity = getReadEntityManager().find(TeamEntity.class, id);
        return Optional.ofNullable(TeamMapper.toDomain(entity));
    }

    @Override
    public List<Team> findAll() {
        List<TeamEntity> entities = getReadEntityManager()
                .createQuery("SELECT t FROM TeamEntity t ORDER BY t.name", TeamEntity.class)
                .getResultList();
        return entities.stream()
                .map(TeamMapper::toDomain)
                .collect(Collectors.toList());
    }

    @Override
    public List<Team> findByGameId(UUID gameId) {
        List<TeamEntity> entities = getReadEntityManager()
                .createQuery("SELECT t FROM TeamEntity t WHERE t.game.id = :gameId ORDER BY t.name", TeamEntity.class)
                .setParameter("gameId", gameId)
                .getResultList();
        return entities.stream()
                .map(TeamMapper::toDomain)
                .collect(Collectors.toList());
    }

    @Override
    public Optional<Team> findByDeviceId(UUID deviceId) {
        try {
            TeamEntity entity = getReadEntityManager()
                    .createQuery("SELECT t FROM TeamEntity t WHERE t.device.id = :deviceId", TeamEntity.class)
                    .setParameter("deviceId", deviceId)
                    .getSingleResult();
            return Optional.ofNullable(TeamMapper.toDomain(entity));
        } catch (NoResultException e) {
            return Optional.empty();
        }
    }


    @Override
    public Optional<Team> findByTraccarIdentifier(String traccarIdentifier) {
        try {
            TeamEntity entity = getReadEntityManager()
                    .createQuery("SELECT t FROM TeamEntity t WHERE t.traccarIdentifier = :traccarIdentifier", TeamEntity.class)
                    .setParameter("traccarIdentifier", traccarIdentifier)
                    .getSingleResult();
            return Optional.ofNullable(TeamMapper.toDomain(entity));
        } catch (NoResultException e) {
            return Optional.empty();
        }
    }

    @Override
    public List<Team> findRivals(UUID teamId) {
        // First, get the team with its rivals eagerly fetched
        try {
            TeamEntity team = getReadEntityManager()
                    .createQuery("SELECT t FROM TeamEntity t LEFT JOIN FETCH t.rivals WHERE t.id = :teamId", TeamEntity.class)
                    .setParameter("teamId", teamId)
                    .getSingleResult();
            
            return team.getRivals().stream()
                    .map(TeamMapper::toDomain)
                    .collect(Collectors.toList());
        } catch (NoResultException e) {
            return Collections.emptyList();
        }
    }

    @Override
    public Optional<Team> findByGameIdAndExternalId(UUID gameId, String externalId) {
        try {
            TeamEntity entity = getReadEntityManager()
                    .createQuery("SELECT t FROM TeamEntity t WHERE t.game.id = :gameId AND t.externalId = :externalId", TeamEntity.class)
                    .setParameter("gameId", gameId)
                    .setParameter("externalId", externalId)
                    .getSingleResult();
            return Optional.ofNullable(TeamMapper.toDomain(entity));
        } catch (NoResultException e) {
            return Optional.empty();
        }
    }

    @Override
    @Transactional
    public Team save(Team team) {
        TeamEntity entity = getWriteEntityManager().find(TeamEntity.class, team.getId());
        
        if (entity == null) {
            // New entity
            entity = TeamMapper.toEntity(team);
            
            // Set game relationship
            if (team.getGameId() != null) {
                GameEntity game = getWriteEntityManager().find(GameEntity.class, team.getGameId());
                entity.setGame(game);
            }
            
            // Set device relationship
            if (team.getDeviceId() != null) {
                DeviceRegistrationEntity device = getWriteEntityManager().find(DeviceRegistrationEntity.class, team.getDeviceId());
                entity.setDevice(device);
            }
            
            // Set rivals
            updateRivals(entity, team.getRivals());
            
            getWriteEntityManager().persist(entity);
        } else {
            // Update existing
            TeamMapper.updateEntity(entity, team);
            entity.setUpdatedAt(Instant.now());
            
            // Update game if changed
            UUID existingGameId = entity.getGame() != null ? entity.getGame().getId() : null;
            if (!Objects.equals(existingGameId, team.getGameId())) {
                if (team.getGameId() != null) {
                    GameEntity game = getWriteEntityManager().find(GameEntity.class, team.getGameId());
                    entity.setGame(game);
                } else {
                    entity.setGame(null);
                }
            }
            
            // Update device if changed
            UUID existingDeviceId = entity.getDevice() != null ? entity.getDevice().getId() : null;
            if (!Objects.equals(existingDeviceId, team.getDeviceId())) {
                if (team.getDeviceId() != null) {
                    DeviceRegistrationEntity device = getWriteEntityManager().find(DeviceRegistrationEntity.class, team.getDeviceId());
                    entity.setDevice(device);
                } else {
                    entity.setDevice(null);
                }
            }
            
            // Update rivals
            updateRivals(entity, team.getRivals());
            
            entity = getWriteEntityManager().merge(entity);
        }
        
        return TeamMapper.toDomain(entity);
    }

    @Override
    @Transactional
    public void delete(UUID id) {
        TeamEntity entity = getWriteEntityManager().find(TeamEntity.class, id);
        if (entity != null) {
            getWriteEntityManager().remove(entity);
        }
    }

    /**
     * Update the rivals relationship on a team entity.
     * Validates that no self-references are added.
     */
    private void updateRivals(TeamEntity entity, List<UUID> rivalIds) {
        entity.getRivals().clear();
        
        if (rivalIds == null || rivalIds.isEmpty()) {
            return;
        }
        
        for (UUID rivalId : rivalIds) {
            // Validate no self-reference
            TeamMapper.validateRivalAddition(entity.getId(), rivalId);
            
            TeamEntity rival = getWriteEntityManager().find(TeamEntity.class, rivalId);
            if (rival != null) {
                entity.getRivals().add(rival);
            }
        }
    }
}
