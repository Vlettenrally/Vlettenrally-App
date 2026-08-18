package nl.vlettenrally.data.converter;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import nl.vlettenrally.data.dto.team.TeamPlayerResponse;
import nl.vlettenrally.data.dto.team.TeamPublicResponse;
import nl.vlettenrally.data.dto.team.TeamResponse;
import nl.vlettenrally.data.domain.Team;
import nl.vlettenrally.service.PointsTotalsCache;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

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
        return toDto(domain, pointsTotalsCache.get(domain.getId()));
    }

    /**
     * Serialize a list of teams, resolving every points total in one bulk lookup.
     * <p>
     * Prefer this over mapping {@link #toDto(Team)} across a list: that form resolves the total
     * per team, which is one Valkey round trip per team.
     */
    public List<TeamResponse> toDtoList(List<Team> domains) {
        if (domains == null || domains.isEmpty()) {
            return List.of();
        }
        List<UUID> teamIds = domains.stream()
                .filter(Objects::nonNull)
                .map(Team::getId)
                .toList();
        Map<UUID, Integer> totals = pointsTotalsCache.getAll(teamIds);
        return domains.stream()
                .map(domain -> domain == null ? null : toDto(domain, totals.getOrDefault(domain.getId(), 0)))
                .toList();
    }

    private TeamResponse toDto(Team domain, int totalPoints) {
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
                domain.isFreeClass(),
                domain.isDisqualified(),
                domain.getDisqualifiedAt() != null ? domain.getDisqualifiedAt().toString() : null,
                domain.isTraccarEnabled(),
                domain.getTraccarIdentifier(),
                domain.getTrackerId(),
                domain.isTrackerHandedIn(),
                domain.getTrackerHandedInAt() != null ? domain.getTrackerHandedInAt().toString() : null,
                totalPoints,
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
