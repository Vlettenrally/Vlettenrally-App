package nl.vlettenrally.service;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import nl.vlettenrally.data.domain.Team;

/**
 * Service interface for Team operations
 */
public interface TeamService {
    
    Optional<Team> findById(UUID id);
    
    List<Team> findAll();
    
    List<Team> findByGameId(UUID gameId);
    
    Optional<Team> findByDeviceId(UUID deviceId);
    
    List<Team> findRivals(UUID teamId);
    
    Optional<Team> findRivalTeam(UUID teamId);
    
    Team save(Team team);
    
    Team checkIn(UUID teamId);
    
    Team updatePoints(UUID teamId, int points);
    
    void delete(UUID id);

    /**
     * Find all rival teams for a given team
     */
    List<Team> findRivalTeams(UUID teamId);
}
