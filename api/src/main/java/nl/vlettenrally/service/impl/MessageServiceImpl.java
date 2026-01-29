package nl.vlettenrally.service.impl;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Any;
import jakarta.enterprise.inject.Instance;
import jakarta.inject.Inject;
import nl.vlettenrally.data.domain.DeviceRegistration;
import nl.vlettenrally.data.domain.Message;
import nl.vlettenrally.data.domain.Team;
import nl.vlettenrally.data.repository.DeviceRegistrationRepository;
import nl.vlettenrally.data.repository.MessageRepository;
import nl.vlettenrally.data.repository.TeamRepository;
import nl.vlettenrally.data.enums.MessageType;
import nl.vlettenrally.data.enums.TransmissionChannel;
import nl.vlettenrally.provider.MessageTransmitter;
import nl.vlettenrally.provider.TransmissionResult;
import nl.vlettenrally.service.MessageService;
import nl.vlettenrally.websockets.WebSocketPublisher;
import nl.vlettenrally.websockets.WebSocketSessionManager;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.jboss.logging.Logger;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Implementation of MessageService with multi-channel transmission support.
 * 
 * Transmission strategy:
 * 1. Always attempt WebSocket broadcast (real-time)
 * 2. Always attempt Firebase push notification (if FCM token exists)
 * 3. If team is inactive AND message is critical, fallback to SMS
 */
@ApplicationScoped
public class MessageServiceImpl implements MessageService {

    private static final Logger LOG = Logger.getLogger(MessageServiceImpl.class);

    @Inject
    MessageRepository messageRepository;

    @Inject
    TeamRepository teamRepository;

    @Inject
    DeviceRegistrationRepository deviceRegistrationRepository;

    @Inject
    WebSocketPublisher webSocketPublisher;

    @Inject
    WebSocketSessionManager webSocketSessionManager;

    @Inject
    @Any
    Instance<MessageTransmitter> transmitters;

    @ConfigProperty(name = "vlettenrally.team.inactive-threshold-minutes", defaultValue = "5")
    int inactiveThresholdMinutes;

    @Override
    public Optional<Message> findById(UUID id) {
        return messageRepository.findById(id);
    }

    @Override
    public List<Message> findAll() {
        return messageRepository.findAll();
    }

    @Override
    public List<Message> findByTeamId(UUID teamId) {
        return messageRepository.findByTeamId(teamId);
    }

    @Override
    public List<Message> findUnreadByTeamId(UUID teamId) {
        return messageRepository.findUnreadByTeamId(teamId);
    }

    @Override
    public int countUnreadByTeamId(UUID teamId) {
        return messageRepository.findUnreadByTeamId(teamId).size();
    }

    @Override
    public Message send(UUID teamId, MessageType type, String content, String icon) {
        Message message = new Message();
        message.setTeamId(teamId);
        message.setMessageType(type);
        message.setContent(content);
        message.setIcon(icon);
        message.setSentAt(Instant.now());
        message.setDelivered(true);
        message.setRead(false);
        
        // Save message first
        Message savedMessage = messageRepository.save(message);
        
        // Attempt transmission via all channels
        transmitMessage(savedMessage);
        
        // Save again to persist transmission status
        return messageRepository.save(savedMessage);
    }

    /**
     * Transmit a message through all appropriate channels.
     * 
     * Strategy:
     * 1. Always attempt WebSocket broadcast
     * 2. Always attempt Firebase push (if token exists)
     * 3. If team inactive AND message is critical, send SMS
     */
    private void transmitMessage(Message message) {
        Optional<Team> teamOpt = teamRepository.findById(message.getTeamId());
        if (teamOpt.isEmpty()) {
            LOG.warnf("Cannot transmit message %s: team %s not found", 
                    message.getId(), message.getTeamId());
            return;
        }
        
        Team team = teamOpt.get();
        List<String> errors = new ArrayList<>();
        
        // 1. WebSocket broadcast (always attempt)
        boolean webSocketDelivered = transmitViaWebSocket(message, team);
        if (webSocketDelivered) {
            message.addTransmissionChannel(TransmissionChannel.WEBSOCKET);
        }
        
        // 2. Check if team is inactive (for SMS fallback decision)
        boolean teamIsInactive = isTeamInactive(team);
        
        // 3. Transmit via registered transmitters (Firebase, SMS)
        for (MessageTransmitter transmitter : transmitters) {
            TransmissionChannel channel = transmitter.getChannel();
            
            // Skip SMS unless team is inactive AND message is critical
            if (channel == TransmissionChannel.SMS) {
                if (!teamIsInactive || !message.getMessageType().isCritical()) {
                    LOG.debugf("Skipping SMS for message %s: inactive=%s, critical=%s",
                            message.getId(), teamIsInactive, message.getMessageType().isCritical());
                    continue;
                }
            }
            
            if (!transmitter.supports(team)) {
                LOG.debugf("Transmitter %s does not support team %s", 
                        channel, team.getName());
                continue;
            }
            
            TransmissionResult result = transmitter.transmit(message, team);
            
            if (result.isSuccess()) {
                message.addTransmissionChannel(channel);
                LOG.infof("Message %s transmitted via %s to team %s", 
                        message.getId(), channel, team.getName());
            } else {
                errors.add(channel + ": " + result.getErrorMessage());
                LOG.warnf("Failed to transmit message %s via %s: %s", 
                        message.getId(), channel, result.getErrorMessage());
            }
        }
        
        // Record any errors
        if (!errors.isEmpty()) {
            message.setTransmissionError(String.join("; ", errors));
        }
    }

    /**
     * Transmit message via WebSocket to the team.
     */
    private boolean transmitViaWebSocket(Message message, Team team) {
        try {
            // Check if team has an active WebSocket session
            var session = webSocketSessionManager.getSessionByTeam(team.getId());
            if (session == null) {
                LOG.debugf("No active WebSocket session for team %s", team.getName());
                return false;
            }
            
            // Send via WebSocket publisher
            webSocketPublisher.sendToTeam(team.getGameId(), team.getId(), "message:new", message);
            return true;
        } catch (Exception e) {
            LOG.errorf("Failed to send WebSocket message to team %s: %s", 
                    team.getName(), e.getMessage());
            return false;
        }
    }

    /**
     * Check if a team's device has been inactive for longer than the threshold.
     */
    private boolean isTeamInactive(Team team) {
        Optional<DeviceRegistration> deviceOpt = deviceRegistrationRepository.findByTeamId(team.getId());
        if (deviceOpt.isEmpty()) {
            // No device registered = considered inactive
            return true;
        }
        
        return deviceOpt.get().isInactive(inactiveThresholdMinutes);
    }

    @Override
    public void markAsRead(UUID id) {
        messageRepository.markAsRead(id);
    }

    @Override
    public void markAllAsReadForTeam(UUID teamId) {
        messageRepository.markAllAsReadForTeam(teamId);
    }

    @Override
    public Message save(Message message) {
        return messageRepository.save(message);
    }

    @Override
    public void delete(UUID id) {
        messageRepository.delete(id);
    }
}
