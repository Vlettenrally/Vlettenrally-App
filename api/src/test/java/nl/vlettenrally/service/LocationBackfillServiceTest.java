package nl.vlettenrally.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.UUID;

import nl.vlettenrally.client.LocationApiClient;
import nl.vlettenrally.data.domain.Team;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * Unit tests for the throttling/orchestration of {@link LocationBackfillService}. The data-path
 * behaviour lives in {@link LocationBackfillPersisterTest}; here we assert that the cooldown/lock
 * gate short-circuits before any provider call or persistence.
 */
@ExtendWith(MockitoExtension.class)
class LocationBackfillServiceTest {

    private static final UUID GAME_ID = UUID.fromString("a1b2c3d4-e5f6-7890-abcd-ef1234567890");

    @Mock LocationApiClient locationApiClient;
    @Mock LocationBackfillPersister persister;
    @Mock GameService gameService;
    @Mock nl.vlettenrally.data.repository.TeamRepository teamRepository;
    @Mock BackfillThrottle throttle;

    private LocationBackfillService service;
    private final Team team = new Team();

    @BeforeEach
    void setUp() {
        service = new LocationBackfillService();
        service.locationApiClient = locationApiClient;
        service.persister = persister;
        service.gameService = gameService;
        service.teamRepository = teamRepository;
        service.throttle = throttle;
    }

    @Test
    void run_returnsBusyWhenLockHeld() throws Exception {
        when(throttle.tryAcquireLock(team.getId())).thenReturn(false);

        LocationBackfillService.BackfillResult result =
            service.backfillTeamTrack(GAME_ID, team.getId(), 82792);

        assertEquals(LocationBackfillService.Status.BUSY, result.status());
        verify(locationApiClient, never()).fetchParticipantTrack(any(), anyInt());
        verify(persister, never()).persist(any(), any(), any());
    }

    @Test
    void run_returnsCoolingDownWhenThrottled() throws Exception {
        when(throttle.tryAcquireLock(team.getId())).thenReturn(true);
        when(throttle.tryStartCooldown(team.getId())).thenReturn(false);
        when(throttle.cooldownRemainingSeconds(team.getId())).thenReturn(120L);

        LocationBackfillService.BackfillResult result =
            service.backfillTeamTrack(GAME_ID, team.getId(), 82792);

        assertEquals(LocationBackfillService.Status.COOLING_DOWN, result.status());
        assertEquals(120L, result.cooldownRemainingSeconds());
        verify(throttle).releaseLock(team.getId());
        verify(persister, never()).persist(any(), any(), any());
    }
}
