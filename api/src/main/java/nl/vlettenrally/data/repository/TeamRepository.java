package nl.vlettenrally.data.repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import nl.vlettenrally.data.domain.Team;

/**
 * Repository interface for Team entities
 */
public interface TeamRepository {
    
    Optional<Team> findById(UUID id);
    
    List<Team> findAll();
    
    List<Team> findByGameId(UUID gameId);
    
    Optional<Team> findByDeviceId(UUID deviceId);
    
    Optional<Team> findByBoatNumber(String boatNumber);
    
    List<Team> findRivals(UUID teamId);
    
    Optional<Team> findByGameIdAndExternalId(UUID gameId, String externalId);
    
    Team save(Team team);
    
    void delete(UUID id);
}
    