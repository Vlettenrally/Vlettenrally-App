package nl.vlettenrally.service.impl;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import nl.vlettenrally.data.domain.Message;
import nl.vlettenrally.data.repository.MessageRepository;
import nl.vlettenrally.service.MessageService;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Implementation of MessageService
 */
@ApplicationScoped
public class MessageServiceImpl implements MessageService {

    @Inject
    MessageRepository messageRepository;

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
    public Message send(UUID teamId, Message.MessageType type, String content, String icon) {
        Message message = new Message();
        message.setTeamId(teamId);
        message.setMessageType(type);
        message.setContent(content);
        message.setIcon(icon);
        message.setSentAt(Instant.now());
        message.setDelivered(true);
        message.setRead(false);
        
        return messageRepository.save(message);
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
