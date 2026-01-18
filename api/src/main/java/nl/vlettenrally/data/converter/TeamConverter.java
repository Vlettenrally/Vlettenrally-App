package nl.vlettenrally.data.converter;

import jakarta.enterprise.context.ApplicationScoped;
import nl.vlettenrally.data.dto.team.TeamResponse;
import nl.vlettenrally.data.domain.Team;

@ApplicationScoped
public class TeamConverter {
    public Team toDomain(TeamResponse dto) {
        if (dto == null) {
            return null;
        }
        Team team = new Team(
                dto.id(),
                dto.game(),
                dto.name(),
                dto.description(),
                dto.boatNumber(),
                dto.color());
        return team;
    }
    
    public TeamResponse toDto(Team domain) {
        if (domain == null) {
            return null;
        }
        return new TeamResponse(
                domain.getId(),
                domain.getGameId(),
                domain.getName(),
                domain.getDescription(),
                domain.getBoatNumber(),
                domain.getColor(),
                domain.getParticipants(),
                domain.getDeviceId(),
                domain.isCheckedIn(),
                domain.getPoints(),
                domain.getRivals(),
                domain.getNotes(),
                domain.getEmergencyPhoneNumbers(),
                domain.getCreatedAt().toString(),
                domain.getUpdatedAt().toString()
        );
    }
}
