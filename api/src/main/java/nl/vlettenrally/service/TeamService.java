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

    Team save(Team team);

    Team checkIn(UUID teamId);

    void delete(UUID id);

    /**
     * Find all rival teams for a given team
     */
    List<Team> findRivalTeams(UUID teamId);

    /**
     * Replace the team's rival list with the supplied UUIDs.
     *
     * @throws IllegalArgumentException if the request is malformed (null list, self-rival,
     *         duplicates, more than {@code maxRivals}, or a UUID that doesn't belong to a team
     *         in the same game).
     * @throws IllegalStateException if the team's game is already COMPLETED (rivals are frozen).
     */
    List<Team> setRivals(UUID teamId, List<UUID> rivalIds);
}
