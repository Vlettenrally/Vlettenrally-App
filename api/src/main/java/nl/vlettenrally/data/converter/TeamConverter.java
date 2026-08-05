package nl.vlettenrally.data.converter;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import nl.vlettenrally.data.dto.team.TeamPlayerResponse;
import nl.vlettenrally.data.dto.team.TeamPublicResponse;
import nl.vlettenrally.data.dto.team.TeamResponse;
import nl.vlettenrally.data.domain.Team;
import nl.vlettenrally.service.PointsTotalsCache;

@ApplicationScoped
public class TeamConverter {

    @Inject
    PointsTotalsCache pointsTotalsCache;

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
                domain.getCheckInMethod(),
                domain.isFinished(),
                domain.getFinishedAt() != null ? domain.getFinishedAt().toString() : null,
                domain.isTraccarEnabled(),
                domain.getTraccarIdentifier(),
                domain.getTrackerId(),
                pointsTotalsCache.get(domain.getId()),
                domain.getRivals(),
                domain.getNotes(),
                domain.getEmergencyPhoneNumbers(),
                domain.getCreatedAt().toString(),
                domain.getUpdatedAt().toString()
        );
    }

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
                domain.getFinishedAt() != null ? domain.getFinishedAt().toString() : null,
                pointsTotalsCache.get(domain.getId()),
                domain.getRivals(),
                domain.getCreatedAt().toString()
        );
    }

    public TeamPublicResponse toPublicResponse(Team domain) {
        if (domain == null) {
            return null;
        }
        return new TeamPublicResponse(
                domain.getId(),
                domain.getName(),
                pointsTotalsCache.get(domain.getId())
        );
    }
}
