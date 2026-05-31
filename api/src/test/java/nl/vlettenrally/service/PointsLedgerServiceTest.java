package nl.vlettenrally.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Optional;
import java.util.UUID;

import jakarta.transaction.TransactionSynchronizationRegistry;
import jakarta.ws.rs.BadRequestException;
import jakarta.ws.rs.NotFoundException;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import nl.vlettenrally.data.converter.PointEntryConverter;
import nl.vlettenrally.data.domain.PointEntry;
import nl.vlettenrally.data.domain.Team;
import nl.vlettenrally.data.enums.PointEntryType;
import nl.vlettenrally.data.repository.PointEntryRepository;
import nl.vlettenrally.data.repository.TeamRepository;
import nl.vlettenrally.websockets.WebSocketPublisher;

/**
 * Unit tests for PointsLedgerService.
 * Verifies the ledger is the single mutator of Team.totalPoints and enforces
 * the non-negative-total invariant on both create and edit.
 */
@ExtendWith(MockitoExtension.class)
class PointsLedgerServiceTest {

    @Mock PointEntryRepository pointEntryRepository;
    @Mock TeamRepository teamRepository;
    @Mock WebSocketPublisher webSocketPublisher;
    @Mock PointEntryConverter pointEntryConverter;
    @Mock TransactionSynchronizationRegistry txRegistry;

    @InjectMocks PointsLedgerService ledger;

    private UUID teamId;
    private UUID gameId;
    private Team team;

    @BeforeEach
    void setUp() {
        teamId = UUID.randomUUID();
        gameId = UUID.randomUUID();

        team = new Team();
        team.setId(teamId);
        team.setGameId(gameId);
        team.setName("Test team");
        team.setTotalPoints(100);

        lenient().when(pointEntryRepository.save(any(PointEntry.class)))
            .thenAnswer(inv -> inv.getArgument(0));
        lenient().when(teamRepository.save(any(Team.class)))
            .thenAnswer(inv -> inv.getArgument(0));
    }

    @Test
    void createEntry_addsAmountToTeamTotal_andRecordsEntry() {
        when(teamRepository.findById(teamId)).thenReturn(Optional.of(team));

        PointEntry result = ledger.createEntry(teamId, gameId, PointEntryType.CHECKPOINT_VERIFICATION,
            50, null, "Checkpoint X", UUID.randomUUID(), null);

        assertEquals(150, team.getTotalPoints());
        assertEquals(50, result.getOriginalAmount());
        assertEquals(50, result.getCurrentAmount());
        assertEquals(teamId, result.getTeamId());
        assertEquals(PointEntryType.CHECKPOINT_VERIFICATION, result.getEntryType());
        verify(teamRepository).save(team);
    }

    @Test
    void createEntry_appliesNegativeAmount_andDeductsFromTeamTotal() {
        when(teamRepository.findById(teamId)).thenReturn(Optional.of(team));

        ledger.createEntry(teamId, gameId, PointEntryType.MANUAL_ADJUSTMENT,
            -30, null, "Penalty", null, UUID.randomUUID());

        assertEquals(70, team.getTotalPoints());
    }

    @Test
    void createEntry_rejectsAmountThatDrivesTotalNegative() {
        when(teamRepository.findById(teamId)).thenReturn(Optional.of(team));

        assertThrows(BadRequestException.class, () ->
            ledger.createEntry(teamId, gameId, PointEntryType.MANUAL_ADJUSTMENT,
                -101, null, "Too much", null, null)
        );

        // Total unchanged, nothing persisted
        assertEquals(100, team.getTotalPoints());
        verify(pointEntryRepository, never()).save(any());
        verify(teamRepository, never()).save(any());
    }

    @Test
    void createEntry_throwsNotFound_whenTeamMissing() {
        when(teamRepository.findById(teamId)).thenReturn(Optional.empty());

        assertThrows(NotFoundException.class, () ->
            ledger.createEntry(teamId, gameId, PointEntryType.MANUAL_ADJUSTMENT,
                10, null, "Anything", null, null)
        );
    }

    @Test
    void editEntry_appliesDeltaToTeamTotal_andLeavesOriginalAmountUntouched() {
        UUID entryId = UUID.randomUUID();
        PointEntry existing = new PointEntry();
        existing.setId(entryId);
        existing.setTeamId(teamId);
        existing.setGameId(gameId);
        existing.setEntryType(PointEntryType.CHECKPOINT_VERIFICATION);
        existing.setOriginalAmount(120);
        existing.setCurrentAmount(120);

        when(pointEntryRepository.findById(entryId)).thenReturn(Optional.of(existing));
        when(teamRepository.findById(teamId)).thenReturn(Optional.of(team));

        UUID admin = UUID.randomUUID();
        PointEntry result = ledger.editEntry(entryId, 100, "Onreglementair gevaren", admin);

        assertEquals(100, result.getCurrentAmount());
        assertEquals(120, result.getOriginalAmount(), "originalAmount must stay immutable");
        assertEquals(admin, result.getEditedBy());
        assertEquals("Onreglementair gevaren", result.getEditReason());
        assertNotNull(result.getEditedAt());
        // Team total adjusted by delta -20
        assertEquals(80, team.getTotalPoints());
    }

    @Test
    void editEntry_rejectsEditThatDrivesTotalNegative() {
        UUID entryId = UUID.randomUUID();
        PointEntry existing = new PointEntry();
        existing.setId(entryId);
        existing.setTeamId(teamId);
        existing.setGameId(gameId);
        existing.setEntryType(PointEntryType.MANUAL_ADJUSTMENT);
        existing.setOriginalAmount(50);
        existing.setCurrentAmount(50);

        when(pointEntryRepository.findById(entryId)).thenReturn(Optional.of(existing));
        when(teamRepository.findById(teamId)).thenReturn(Optional.of(team));

        // Reducing by 200 would put team at -50
        assertThrows(BadRequestException.class, () ->
            ledger.editEntry(entryId, -150, "Too far", null)
        );

        assertEquals(100, team.getTotalPoints());
        assertEquals(50, existing.getCurrentAmount(), "Entry must not be mutated on rejection");
    }

    @Test
    void editEntry_noOpDelta_stillStampsAuditFields() {
        UUID entryId = UUID.randomUUID();
        PointEntry existing = new PointEntry();
        existing.setId(entryId);
        existing.setTeamId(teamId);
        existing.setGameId(gameId);
        existing.setEntryType(PointEntryType.MANUAL_ADJUSTMENT);
        existing.setOriginalAmount(50);
        existing.setCurrentAmount(50);

        when(pointEntryRepository.findById(entryId)).thenReturn(Optional.of(existing));

        UUID admin = UUID.randomUUID();
        ledger.editEntry(entryId, 50, "No change but record reason", admin);

        // Team should NOT have been looked up at all
        verify(teamRepository, never()).findById(any());
        verify(teamRepository, never()).save(any());
        assertNotNull(existing.getEditedAt());
        assertEquals(admin, existing.getEditedBy());
        assertEquals("No change but record reason", existing.getEditReason());
    }

    @Test
    void editEntry_throwsNotFound_whenEntryMissing() {
        UUID entryId = UUID.randomUUID();
        when(pointEntryRepository.findById(entryId)).thenReturn(Optional.empty());

        assertThrows(NotFoundException.class, () ->
            ledger.editEntry(entryId, 10, "any", null)
        );
    }

    @Test
    void createEntry_persistsSourceAchievementIdAndDescription() {
        when(teamRepository.findById(teamId)).thenReturn(Optional.of(team));

        UUID achievementId = UUID.randomUUID();
        ledger.createEntry(teamId, gameId, PointEntryType.CHECKPOINT_VERIFICATION,
            25, null, "Checkpoint Amstel", achievementId, null);

        ArgumentCaptor<PointEntry> captor = ArgumentCaptor.forClass(PointEntry.class);
        verify(pointEntryRepository).save(captor.capture());

        PointEntry saved = captor.getValue();
        assertEquals(achievementId, saved.getSourceAchievementId());
        assertEquals("Checkpoint Amstel", saved.getDescription());
        assertNull(saved.getCreatedBy(), "System-created entries have no createdBy");
    }

    @Test
    void createEntry_recordsCreatedByForManualAdjustment() {
        when(teamRepository.findById(teamId)).thenReturn(Optional.of(team));

        UUID admin = UUID.randomUUID();
        ledger.createEntry(teamId, gameId, PointEntryType.MANUAL_ADJUSTMENT,
            10, null, "Bonus", null, admin);

        ArgumentCaptor<PointEntry> captor = ArgumentCaptor.forClass(PointEntry.class);
        verify(pointEntryRepository, times(1)).save(captor.capture());

        assertEquals(admin, captor.getValue().getCreatedBy());
        assertEquals(PointEntryType.MANUAL_ADJUSTMENT, captor.getValue().getEntryType());
    }

    @Test
    void editEntry_doesNotMutateTeam_whenDeltaIsZero() {
        UUID entryId = UUID.randomUUID();
        PointEntry existing = new PointEntry();
        existing.setId(entryId);
        existing.setTeamId(teamId);
        existing.setGameId(gameId);
        existing.setCurrentAmount(40);
        existing.setOriginalAmount(40);

        when(pointEntryRepository.findById(eq(entryId))).thenReturn(Optional.of(existing));

        ledger.editEntry(entryId, 40, "reaffirm", null);

        assertEquals(100, team.getTotalPoints());
        verify(teamRepository, never()).save(any());
    }
}
