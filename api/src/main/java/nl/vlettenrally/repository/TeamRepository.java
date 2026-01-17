package nl.vlettenrally.repository;

import nl.vlettenrally.domain.Team;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

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
    
    Team save(Team team);
    
    void delete(UUID id);
}
