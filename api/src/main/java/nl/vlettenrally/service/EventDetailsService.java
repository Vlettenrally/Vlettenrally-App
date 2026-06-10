package nl.vlettenrally.service;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import nl.vlettenrally.client.LocationApiClient;
import nl.vlettenrally.client.dto.legendstracking.EventCheckpoint;
import nl.vlettenrally.client.dto.legendstracking.EventDetailsBody;
import nl.vlettenrally.client.dto.legendstracking.EventDetailsResponse;
import nl.vlettenrally.client.dto.legendstracking.EventParticipant;
import nl.vlettenrally.client.exception.LocationApiException;
import nl.vlettenrally.data.domain.Checkpoint;
import nl.vlettenrally.data.domain.CircleCheckpoint;
import nl.vlettenrally.data.domain.Team;

import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * Service for syncing master data from the Legendstracking /details endpoint.
 *
 * Provides two independent operations against the same endpoint:
 * <ul>
 *   <li>{@link #importCheckpoints} — upserts checkpoints (zones) by
 *       externalId ↔ EventCheckpoint.id.</li>
 *   <li>{@link #linkParticipants} — links each team's externalParticipantId by
 *       matching the participant bib to team.name (case-insensitive trimmed;
 *       falls back to bib_label).</li>
 * </ul>
 *
 * Mutations are delegated to {@link CheckpointService}/{@link TeamService}, whose
 * save operations are individually transactional, so a single failing item does
 * not roll back the rest of the batch.
 */
@ApplicationScoped
public class EventDetailsService {

    @Inject
    LocationApiClient locationApiClient;

    @Inject
    CheckpointService checkpointService;

    @Inject
    TeamService teamService;

    /**
     * Import checkpoints (zones) for a game from the tracker /details endpoint.
     *
     * @param gameId              the game to import checkpoints into
     * @param locationEndpointUrl the tracker /details endpoint URL
     * @return counts of created, updated, and errored checkpoints
     */
    public ImportResult importCheckpoints(UUID gameId, String locationEndpointUrl)
            throws LocationApiException {
        EventDetailsBody body = fetchDetailsBody(locationEndpointUrl);

        int created = 0;
        int updated = 0;
        int errors = 0;

        List<EventCheckpoint> checkpoints = body.getEventCheckpoints();
        if (checkpoints != null) {
            for (EventCheckpoint detail : checkpoints) {
                try {
                    String externalId = String.valueOf(detail.getId());
                    Optional<Checkpoint> existing = checkpointService.findByGameIdAndExternalId(gameId, externalId);

                    if (existing.isPresent()) {
                        Checkpoint cp = existing.get();
                        boolean changed = false;

                        if (detail.getLocation() != null) {
                            Double newLat = detail.getLocation().getLatitude();
                            Double newLng = detail.getLocation().getLongitude();
                            if (!Objects.equals(cp.getLatitude(), newLat)
                                    || !Objects.equals(cp.getLongitude(), newLng)) {
                                cp.setLatitude(newLat);
                                cp.setLongitude(newLng);
                                changed = true;
                            }
                        }

                        if (detail.getName() != null && !detail.getName().equals(cp.getName())) {
                            cp.setName(detail.getName());
                            changed = true;
                        }

                        if (changed) {
                            cp.setUpdatedAt(Instant.now());
                            checkpointService.save(cp);
                            updated++;
                        }
                    } else {
                        CircleCheckpoint cp = new CircleCheckpoint();
                        cp.setGameId(gameId);
                        cp.setExternalId(externalId);
                        cp.setName(detail.getName() != null ? detail.getName() : "Zone " + externalId);
                        cp.setPoints(0);
                        if (detail.getLocation() != null) {
                            cp.setLatitude(detail.getLocation().getLatitude());
                            cp.setLongitude(detail.getLocation().getLongitude());
                        }
                        checkpointService.save(cp);
                        created++;
                    }
                } catch (Exception e) {
                    errors++;
                }
            }
        }

        return new ImportResult(created, updated, errors);
    }

    /**
     * Link teams for a game to tracker participants from the /details endpoint.
     *
     * @param gameId              the game whose teams to link
     * @param locationEndpointUrl the tracker /details endpoint URL
     * @return counts of linked teams and unmatched participants
     */
    public ParticipantLinkResult linkParticipants(UUID gameId, String locationEndpointUrl)
            throws LocationApiException {
        EventDetailsBody body = fetchDetailsBody(locationEndpointUrl);

        int linked = 0;
        int unmatched = 0;

        List<EventParticipant> participants = body.getEventParticipants();
        if (participants != null && !participants.isEmpty()) {
            List<Team> teams = teamService.findByGameId(gameId);
            Map<String, Team> teamsByName = new HashMap<>();
            for (Team t : teams) {
                if (t.getName() != null && !t.getName().isBlank()) {
                    teamsByName.put(t.getName().toLowerCase().trim(), t);
                }
            }

            for (EventParticipant p : participants) {
                Team match = lookupTeamForParticipant(teamsByName, p);
                if (match == null) {
                    unmatched++;
                    continue;
                }
                if (!Integer.valueOf(p.getEventParticipantsId()).equals(match.getExternalParticipantId())) {
                    match.setExternalParticipantId(p.getEventParticipantsId());
                    match.setUpdatedAt(Instant.now());
                    teamService.save(match);
                }
                linked++;
            }
        }

        return new ParticipantLinkResult(linked, unmatched);
    }

    /** Fetches the /details event body, throwing if the response is malformed. */
    private EventDetailsBody fetchDetailsBody(String locationEndpointUrl) throws LocationApiException {
        EventDetailsResponse details = locationApiClient.fetchDetails(locationEndpointUrl);
        EventDetailsBody body = details != null ? details.getEvent() : null;
        if (body == null) {
            throw new LocationApiException("Malformed /details response: missing 'event'");
        }
        return body;
    }

    private static Team lookupTeamForParticipant(Map<String, Team> teamsByName, EventParticipant p) {
        if (p.getBib() != null) {
            Team t = teamsByName.get(String.valueOf(p.getBib()).toLowerCase().trim());
            if (t != null) return t;
        }
        if (p.getBibLabel() != null && !p.getBibLabel().isBlank()) {
            return teamsByName.get(p.getBibLabel().toLowerCase().trim());
        }
        return null;
    }
}
