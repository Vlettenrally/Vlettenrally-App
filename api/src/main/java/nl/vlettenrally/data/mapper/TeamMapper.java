package nl.vlettenrally.data.mapper;

import nl.vlettenrally.data.domain.Team;
import nl.vlettenrally.data.entity.jpa.TeamEntity;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Mapper for converting between Team domain objects and JPA entities.
 */
public class TeamMapper {

    private TeamMapper() {
        // Utility class
    }

    /**
     * Convert a JPA entity to a domain object.
     * Note: This does NOT load rivals to avoid N+1 queries. Use toDomainWithRivals for full conversion.
     */
    public static Team toDomain(TeamEntity entity) {
        if (entity == null) {
            return null;
        }

        Team domain = new Team();
        domain.setId(entity.getId());
        domain.setGameId(entity.getGame() != null ? entity.getGame().getId() : null);
        domain.setName(entity.getName());
        domain.setDescription(entity.getDescription());
        domain.setExternalId(entity.getExternalId());
        domain.setParticipants(new ArrayList<>(entity.getParticipants()));
        domain.setDeviceId(entity.getDevice() != null ? entity.getDevice().getId() : null);
        domain.setMainDeviceId(entity.getMainDeviceId());
        domain.setCheckedIn(entity.isCheckedIn());
        domain.setCheckedInDigitally(entity.isCheckedInDigitally());
        domain.setCheckedInAt(entity.getCheckedInAt());
        domain.setFinished(entity.isFinished());
        domain.setFinishedAt(entity.getFinishedAt());
        domain.setTraccarEnabled(entity.isTraccarEnabled());
        domain.setTraccarIdentifier(entity.getTraccarIdentifier());
        domain.setTrackerId(entity.getTrackerId());
        domain.setTrackerAssignedAt(entity.getTrackerAssignedAt());
        domain.setNotes(entity.getNotes());
        // Set emergency phone numbers without validation (already validated on input)
        domain.getEmergencyPhoneNumbers().clear();
        domain.getEmergencyPhoneNumbers().addAll(entity.getEmergencyPhoneNumbers());
        domain.setCreatedAt(entity.getCreatedAt());
        domain.setUpdatedAt(entity.getUpdatedAt());

        // Convert rival entities to UUIDs (will be empty if not fetched)
        List<UUID> rivalIds = entity.getRivals().stream()
                .map(TeamEntity::getId)
                .collect(Collectors.toList());
        domain.setRivals(rivalIds);

        return domain;
    }

    /**
     * Convert a domain object to a JPA entity.
     * Note: This does NOT set relationships (game, device, rivals). Those must be set separately.
     */
    public static TeamEntity toEntity(Team domain) {
        if (domain == null) {
            return null;
        }

        TeamEntity entity = new TeamEntity();
        entity.setId(domain.getId());
        entity.setName(domain.getName());
        entity.setDescription(domain.getDescription());
        entity.setExternalId(domain.getExternalId());
        entity.setParticipants(new ArrayList<>(domain.getParticipants()));
        entity.setMainDeviceId(domain.getMainDeviceId());
        entity.setCheckedIn(domain.isCheckedIn());
        entity.setCheckedInDigitally(domain.isCheckedInDigitally());
        entity.setCheckedInAt(domain.getCheckedInAt());
        entity.setFinished(domain.isFinished());
        entity.setFinishedAt(domain.getFinishedAt());
        entity.setTraccarEnabled(domain.isTraccarEnabled());
        entity.setTraccarIdentifier(domain.getTraccarIdentifier());
        entity.setTrackerId(domain.getTrackerId());
        entity.setTrackerAssignedAt(domain.getTrackerAssignedAt());
        entity.setNotes(domain.getNotes());
        entity.setEmergencyPhoneNumbers(new ArrayList<>(domain.getEmergencyPhoneNumbers()));
        entity.setCreatedAt(domain.getCreatedAt());
        entity.setUpdatedAt(domain.getUpdatedAt());

        return entity;
    }

    /**
     * Update an existing JPA entity with values from a domain object.
     * Note: This does NOT update relationships. Those must be updated separately.
     */
    public static void updateEntity(TeamEntity entity, Team domain) {
        if (entity == null || domain == null) {
            return;
        }

        entity.setName(domain.getName());
        entity.setDescription(domain.getDescription());
        entity.setExternalId(domain.getExternalId());
        entity.setParticipants(new ArrayList<>(domain.getParticipants()));
        entity.setMainDeviceId(domain.getMainDeviceId());
        entity.setCheckedIn(domain.isCheckedIn());
        entity.setCheckedInDigitally(domain.isCheckedInDigitally());
        entity.setCheckedInAt(domain.getCheckedInAt());
        entity.setFinished(domain.isFinished());
        entity.setFinishedAt(domain.getFinishedAt());
        entity.setTraccarEnabled(domain.isTraccarEnabled());
        entity.setTraccarIdentifier(domain.getTraccarIdentifier());
        entity.setTrackerId(domain.getTrackerId());
        entity.setTrackerAssignedAt(domain.getTrackerAssignedAt());
        entity.setNotes(domain.getNotes());
        entity.setEmergencyPhoneNumbers(new ArrayList<>(domain.getEmergencyPhoneNumbers()));
        entity.setUpdatedAt(domain.getUpdatedAt());
    }

    /**
     * Validate and add a rival to a team.
     * @throws IllegalArgumentException if trying to add self as rival
     */
    public static void validateRivalAddition(UUID teamId, UUID rivalTeamId) {
        if (teamId != null && teamId.equals(rivalTeamId)) {
            throw new IllegalArgumentException("A team cannot be its own rival");
        }
    }

    /**
     * Update the rivals set on an entity from a list of rival entities.
     * Validates that no self-references are added.
     */
    public static void updateRivals(TeamEntity entity, Set<TeamEntity> rivals) {
        if (entity == null) {
            return;
        }

        // Validate no self-references
        for (TeamEntity rival : rivals) {
            validateRivalAddition(entity.getId(), rival.getId());
        }

        entity.getRivals().clear();
        entity.getRivals().addAll(rivals);
    }
}
