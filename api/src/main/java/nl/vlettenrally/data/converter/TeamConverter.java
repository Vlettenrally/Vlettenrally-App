package nl.vlettenrally.data.converter;

import jakarta.enterprise.context.ApplicationScoped;
import nl.vlettenrally.data.dto.team.TeamPlayerResponse;
import nl.vlettenrally.data.dto.team.TeamPublicResponse;
import nl.vlettenrally.data.dto.team.TeamResponse;
import nl.vlettenrally.data.domain.Team;

@ApplicationScoped
public class TeamConverter {
    public Team toDomain(TeamResponse dto) {
        if (dto == null) {
            return null;
        }
        return new Team(
                dto.id(),
                dto.gameId(),
                dto.name(),
                dto.description());
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
                domain.getParticipants(),
                domain.getDeviceId(),
                domain.isCheckedIn(),
                domain.isCheckedInDigitally(),
                domain.isFinished(),
                domain.isTraccarEnabled(),
                domain.getTraccarIdentifier(),
                domain.getTotalPoints(),
                domain.getRivals(),
                domain.getNotes(),
                domain.getEmergencyPhoneNumbers(),
                domain.getCreatedAt().toString(),
                domain.getUpdatedAt().toString()
        );
    }

    /**
     * Convert domain Team to TeamPlayerResponse (limited view for player's own team)
     */
    public TeamPlayerResponse toPlayerResponse(Team domain) {
        if (domain == null) {
            return null;
        }
        return new TeamPlayerResponse(
                domain.getId(),
                domain.getGameId(),
                domain.getName(),
                domain.getDescription(),
                domain.isCheckedIn(),
                domain.isCheckedInDigitally(),
                domain.isFinished(),
                domain.getTotalPoints(),
                domain.getRivals(),
                domain.getCreatedAt().toString()
        );
    }

    /**
     * Convert domain Team to TeamPublicResponse (minimal view for rivals/rankings)
     */
    public TeamPublicResponse toPublicResponse(Team domain) {
        if (domain == null) {
            return null;
        }
        return new TeamPublicResponse(
                domain.getId(),
                domain.getName(),
                domain.getTotalPoints()
        );
    }
}
