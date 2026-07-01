package nl.vlettenrally.service.impl;

import nl.vlettenrally.data.domain.Message;
import nl.vlettenrally.data.domain.Team;
import nl.vlettenrally.data.dto.message.AdminMessageBatchResult;
import nl.vlettenrally.data.dto.message.AdminMessageResponse;
import nl.vlettenrally.data.enums.MessageSource;
import nl.vlettenrally.data.enums.MessageType;
import nl.vlettenrally.data.enums.TransmissionChannel;
import nl.vlettenrally.data.repository.DeviceRegistrationRepository;
import nl.vlettenrally.data.repository.MessageRepository;
import nl.vlettenrally.data.repository.TeamRepository;
import nl.vlettenrally.provider.MessageTransmitter;
import nl.vlettenrally.websockets.WebSocketPublisher;
import nl.vlettenrally.websockets.WebSocketSessionManager;

import jakarta.enterprise.inject.Instance;
import java.util.Collections;
import org.mockito.ArgumentCaptor;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Unit tests for MessageServiceImpl.
 * Tests message operations including sending, marking as read, and admin message batches.
 */
@ExtendWith(MockitoExtension.class)
class MessageServiceImplTest {

    @Mock
    MessageRepository messageRepository;

    @Mock
    TeamRepository teamRepository;

    @Mock
    WebSocketPublisher webSocketPublisher;

    @Mock
    WebSocketSessionManager webSocketSessionManager;

    @Mock
    DeviceRegistrationRepository deviceRegistrationRepository;

    @Mock
    Instance<MessageTransmitter> transmitters;

    @InjectMocks
    MessageServiceImpl messageService;

    private UUID messageId;
    private UUID teamId;
    private UUID gameId;
    private UUID adminId;
    private UUID batchId;

    @BeforeEach
    void setUp() {
        messageId = UUID.randomUUID();
        teamId = UUID.randomUUID();
        gameId = UUID.randomUUID();
        adminId = UUID.randomUUID();
        batchId = UUID.randomUUID();
    }

    // ==================== findById Tests ====================

    @Test
    void findById_messageExists_returnsMessage() {
        // Arrange
        Message message = createMessage();
        message.setId(messageId);
        when(messageRepository.findById(messageId)).thenReturn(Optional.of(message));

        // Act
        Optional<Message> result = messageService.findById(messageId);

        // Assert
        assertTrue(result.isPresent());
        assertEquals(messageId, result.get().getId());
        verify(messageRepository).findById(messageId);
    }

    @Test
    void findById_messageDoesNotExist_returnsEmpty() {
        // Arrange
        when(messageRepository.findById(messageId)).thenReturn(Optional.empty());

        // Act
        Optional<Message> result = messageService.findById(messageId);

        // Assert
        assertFalse(result.isPresent());
        verify(messageRepository).findById(messageId);
    }

    // ==================== findAll Tests ====================

    @Test
    void findAll_returnsAllMessages() {
        // Arrange
        List<Message> messages = List.of(createMessage(), createMessage(), createMessage());
        when(messageRepository.findAll()).thenReturn(messages);

        // Act
        List<Message> result = messageService.findAll();

        // Assert
        assertEquals(3, result.size());
        verify(messageRepository).findAll();
    }

    // ==================== findByTeamId Tests ====================

    @Test
    void findByTeamId_returnsMessagesForTeam() {
        // Arrange
        List<Message> messages = List.of(createMessage(), createMessage());
        when(messageRepository.findByTeamId(teamId)).thenReturn(messages);

        // Act
        List<Message> result = messageService.findByTeamId(teamId);

        // Assert
        assertEquals(2, result.size());
        verify(messageRepository).findByTeamId(teamId);
    }

    // ==================== countUnreadByTeamId Tests ====================

    @Test
    void countUnreadByTeamId_returnsUnreadCount() {
        // Arrange
        Message msg1 = createMessage();
        msg1.setRead(false);
        Message msg2 = createMessage();
        msg2.setRead(true);
        Message msg3 = createMessage();
        msg3.setRead(false);

        when(messageRepository.findUnreadByTeamId(teamId)).thenReturn(List.of(msg1, msg3));

        // Act
        int result = messageService.countUnreadByTeamId(teamId);

        // Assert
        assertEquals(2, result);
        verify(messageRepository).findUnreadByTeamId(teamId);
    }

    // ==================== markAsRead Tests ====================

    @Test
    void markAsRead_messageExists_marksAsRead() {
        // Arrange
        Message message = createMessage();
        message.setId(messageId);
        when(messageRepository.findById(messageId)).thenReturn(Optional.of(message));

        // Act
        messageService.markAsRead(messageId);

        // Assert
        verify(messageRepository).findById(messageId);
        verify(messageRepository).markAsRead(messageId);
        verify(webSocketPublisher).sendToAdminTopic(eq(message.getGameId()), eq("messages"), eq("MESSAGE_DELIVERY_UPDATE"), any());
    }

    @Test
    void markAsRead_messageDoesNotExist_logsWarning() {
        // Arrange
        when(messageRepository.findById(messageId)).thenReturn(Optional.empty());

        // Act
        messageService.markAsRead(messageId);

        // Assert
        verify(messageRepository).findById(messageId);
        verify(messageRepository, never()).markAsRead(messageId);
    }

    // ==================== markMultipleAsRead Tests ====================

    @Test
    void markMultipleAsRead_marksAllAsRead() {
        // Arrange
        UUID id1 = UUID.randomUUID();
        UUID id2 = UUID.randomUUID();
        List<UUID> ids = List.of(id1, id2);

        Message msg1 = createMessage();
        Message msg2 = createMessage();

        when(messageRepository.findById(id1)).thenReturn(Optional.of(msg1));
        when(messageRepository.findById(id2)).thenReturn(Optional.of(msg2));

        // Act
        messageService.markMultipleAsRead(ids);

        // Assert
        verify(messageRepository).markAsRead(id1);
        verify(messageRepository).markAsRead(id2);
        verify(webSocketPublisher, times(2)).sendToAdminTopic(eq(msg1.getGameId()), eq("messages"), eq("MESSAGE_DELIVERY_UPDATE"), any());
    }

    @Test
    void markMultipleAsRead_emptyList_doesNothing() {
        // Act
        messageService.markMultipleAsRead(List.of());

        // Assert
        verify(messageRepository, never()).markAsRead(any());
    }

    @Test
    void markMultipleAsRead_nullList_doesNothing() {
        // Act
        messageService.markMultipleAsRead(null);

        // Assert
        verify(messageRepository, never()).markAsRead(any());
    }

    // ==================== markAllAsReadForTeam Tests ====================

    @Test
    void markAllAsReadForTeam_marksAllTeamMessagesAsRead() {
        // Arrange
        Message msg1 = createMessage();
        Message msg2 = createMessage();
        when(messageRepository.findUnreadByTeamId(teamId)).thenReturn(List.of(msg1, msg2));

        // Act
        messageService.markAllAsReadForTeam(teamId);

        // Assert
        verify(messageRepository).markAllAsReadForTeam(teamId);
        verify(webSocketPublisher, times(2)).sendToAdminTopic(eq(msg1.getGameId()), eq("messages"), eq("MESSAGE_DELIVERY_UPDATE"), any());
    }

    // ==================== findAdminMessagesByGame Tests ====================

    @Test
    void findAdminMessagesByGame_returnsRepresentativeMessages() {
        // Arrange
        Message msg = createMessage();
        when(messageRepository.findDistinctBatchesByGameIdPaginated(gameId, MessageSource.ADMIN, 0, 20))
                .thenReturn(List.of(msg));

        // Act
        List<Message> result = messageService.findAdminMessagesByGame(gameId, 0, 20);

        // Assert
        assertEquals(1, result.size());
        verify(messageRepository).findDistinctBatchesByGameIdPaginated(gameId, MessageSource.ADMIN, 0, 20);
    }

    // ==================== countAdminMessagesByGame Tests ====================

    @Test
    void countAdminMessagesByGame_returnsCount() {
        // Arrange
        when(messageRepository.countDistinctBatchesByGameIdAndSource(gameId, MessageSource.ADMIN))
                .thenReturn(5L);

        // Act
        long result = messageService.countAdminMessagesByGame(gameId);

        // Assert
        assertEquals(5L, result);
        verify(messageRepository).countDistinctBatchesByGameIdAndSource(gameId, MessageSource.ADMIN);
    }

    // ==================== getMessagesInBatch Tests ====================

    @Test
    void getMessagesInBatch_returnsMessagesInBatch() {
        // Arrange
        List<Message> messages = List.of(createMessage(), createMessage());
        when(messageRepository.findByBatchId(batchId)).thenReturn(messages);

        // Act
        List<Message> result = messageService.getMessagesInBatch(batchId);

        // Assert
        assertEquals(2, result.size());
        verify(messageRepository).findByBatchId(batchId);
    }

    // ==================== buildAdminMessageResponse Tests ====================

    @Test
    void buildAdminMessageResponse_withBatchResult_buildsResponseCorrectly() {
        // Arrange
        Message msg1 = createMessage();
        msg1.setTeamId(teamId);
        msg1.setDelivered(true);
        msg1.addTransmissionChannel(TransmissionChannel.WEBSOCKET);

        Message msg2 = createMessage();
        msg2.setTeamId(UUID.randomUUID());
        msg2.setDelivered(false);

        Team team1 = createTeam("Team A");
        Team team2 = createTeam("Team B");

        when(teamRepository.findById(msg1.getTeamId())).thenReturn(Optional.of(team1));
        when(teamRepository.findById(msg2.getTeamId())).thenReturn(Optional.of(team2));

        AdminMessageBatchResult batchResult = new AdminMessageBatchResult(
                batchId, 2, 1, 1, Instant.now()
        );

        // Act
        AdminMessageResponse response = messageService.buildAdminMessageResponse(batchResult, List.of(msg1, msg2));

        // Assert
        assertEquals(batchId, response.id());
        assertEquals(2, response.deliveries().size());
        assertEquals(1, response.deliveredCount());
        assertEquals(1, response.failedCount());
        assertTrue(response.deliveries().getFirst().delivered());
        assertFalse(response.deliveries().getLast().delivered());
        verify(teamRepository, times(2)).findById(any());
    }

    @Test
    void buildAdminMessageResponse_withRepresentativeMessage_buildsResponseCorrectly() {
        // Arrange
        Message representative = createMessage();
        representative.setTeamId(teamId);
        representative.setBatchId(batchId);

        Message msg1 = createMessage();
        msg1.setTeamId(teamId);
        msg1.setDelivered(true);

        Message msg2 = createMessage();
        msg2.setTeamId(UUID.randomUUID());
        msg2.setDelivered(false);

        Team team1 = createTeam("Team A");
        Team team2 = createTeam("Team B");

        when(teamRepository.findById(msg1.getTeamId())).thenReturn(Optional.of(team1));
        when(teamRepository.findById(msg2.getTeamId())).thenReturn(Optional.of(team2));

        // Act
        AdminMessageResponse response = messageService.buildAdminMessageResponse(representative, List.of(msg1, msg2));

        // Assert
        assertEquals(batchId, response.id());
        assertEquals(2, response.totalRecipients());
        assertEquals(1, response.deliveredCount());
        assertEquals(1, response.failedCount());
        assertEquals(2, response.deliveries().size());
    }

    @Test
    void buildAdminMessageResponse_emptyMessageList_handlesGracefully() {
        // Arrange
        AdminMessageBatchResult batchResult = new AdminMessageBatchResult(
                batchId, 0, 0, 0, Instant.now()
        );

        // Act
        AdminMessageResponse response = messageService.buildAdminMessageResponse(batchResult, List.of());

        // Assert
        assertEquals(batchId, response.id());
        assertEquals(0, response.deliveries().size());
        assertNull(response.gameId());
    }

    @Test
    void buildAdminMessageResponse_teamNotFound_usesUnknown() {
        // Arrange
        Message msg = createMessage();
        msg.setTeamId(teamId);
        msg.setDelivered(true);

        when(teamRepository.findById(teamId)).thenReturn(Optional.empty());

        AdminMessageBatchResult batchResult = new AdminMessageBatchResult(
                batchId, 1, 1, 0, Instant.now()
        );

        // Act
        AdminMessageResponse response = messageService.buildAdminMessageResponse(batchResult, List.of(msg));

        // Assert
        assertEquals(1, response.deliveries().size());
        assertEquals("Unknown", response.deliveries().getFirst().teamName());
    }

    // ==================== Helper Methods ====================

    // ==================== sendAutomationMessage Tests ====================

    @Test
    void sendAutomationMessage_tagsMessagesWithAutomationSource_andNoAdmin() {
        Team team = createTeam("De Watervlooien");
        when(teamRepository.findById(team.getId())).thenReturn(Optional.of(team));
        when(messageRepository.save(any(Message.class))).thenAnswer(inv -> inv.getArgument(0));
        when(webSocketSessionManager.getSessionByTeam(team.getId())).thenReturn(null);
        when(deviceRegistrationRepository.findAllByTeamId(team.getId())).thenReturn(List.of());
        when(transmitters.iterator()).thenReturn(Collections.emptyIterator());

        AdminMessageBatchResult result = messageService.sendAutomationMessage(
            gameId, List.of(team.getId()), MessageType.GENERAL, "Goed bezig!", "🎉", UUID.randomUUID());

        assertEquals(1, result.totalTeams());
        ArgumentCaptor<Message> captor = ArgumentCaptor.forClass(Message.class);
        verify(messageRepository, atLeastOnce()).save(captor.capture());
        Message saved = captor.getAllValues().getFirst();
        assertEquals(MessageSource.AUTOMATION, saved.getSource());
        assertNull(saved.getSentByAdminId());
        assertEquals("Goed bezig!", saved.getContent());
    }

    @Test
    void sendAdminMessage_resolvesTeamPlaceholderPerRecipient() {
        Team team1 = createTeam("De Watervlooien");
        Team team2 = createTeam("Windkracht Tien");
        when(teamRepository.findByGameId(gameId)).thenReturn(List.of(team1, team2));
        when(teamRepository.findById(team1.getId())).thenReturn(Optional.of(team1));
        when(teamRepository.findById(team2.getId())).thenReturn(Optional.of(team2));
        when(messageRepository.save(any(Message.class))).thenAnswer(inv -> inv.getArgument(0));
        when(webSocketSessionManager.getSessionByTeam(any())).thenReturn(null);
        when(deviceRegistrationRepository.findAllByTeamId(any())).thenReturn(List.of());
        when(transmitters.iterator()).thenReturn(Collections.emptyIterator());

        messageService.sendAdminMessage(gameId, null, MessageType.GENERAL, "Hoi {team}!", null, adminId);

        ArgumentCaptor<Message> captor = ArgumentCaptor.forClass(Message.class);
        verify(messageRepository, atLeastOnce()).save(captor.capture());
        // Each recipient's stored content shows its own name.
        assertTrue(captor.getAllValues().stream().anyMatch(m -> "Hoi De Watervlooien!".equals(m.getContent())));
        assertTrue(captor.getAllValues().stream().anyMatch(m -> "Hoi Windkracht Tien!".equals(m.getContent())));
    }

    private Message createMessage() {
        Message message = new Message();
        message.setId(UUID.randomUUID());
        message.setTeamId(teamId);
        message.setGameId(gameId);
        message.setMessageType(MessageType.GENERAL);
        message.setContent("Test message");
        message.setIcon("info-icon");
        message.setSentAt(Instant.now());
        message.setDelivered(true);
        message.setRead(false);
        message.setSource(MessageSource.ADMIN);
        message.setSentByAdminId(adminId);
        message.setBatchId(batchId);
        return message;
    }

    private Team createTeam(String name) {
        Team team = new Team();
        team.setId(UUID.randomUUID());
        team.setGameId(gameId);
        team.setName(name);
        return team;
    }
}
