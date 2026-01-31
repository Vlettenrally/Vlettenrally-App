package nl.vlettenrally.service.impl;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import nl.vlettenrally.data.domain.MessageTemplate;
import nl.vlettenrally.data.enums.MessageType;
import nl.vlettenrally.data.repository.MessageTemplateRepository;
import nl.vlettenrally.service.MessageTemplateService;
import org.jboss.logging.Logger;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Implementation of MessageTemplateService.
 */
@ApplicationScoped
public class MessageTemplateServiceImpl implements MessageTemplateService {

    private static final Logger LOG = Logger.getLogger(MessageTemplateServiceImpl.class);

    @Inject
    MessageTemplateRepository messageTemplateRepository;

    @Override
    public Optional<MessageTemplate> findById(UUID id) {
        return messageTemplateRepository.findById(id);
    }

    @Override
    public List<MessageTemplate> findAll() {
        return messageTemplateRepository.findAll();
    }

    @Override
    public MessageTemplate create(String name, MessageType messageType, String content, String icon) {
        MessageTemplate template = new MessageTemplate();
        template.setName(name);
        template.setMessageType(messageType);
        template.setContent(content);
        template.setIcon(icon);
        
        MessageTemplate saved = messageTemplateRepository.save(template);
        LOG.infof("Created message template: %s", saved.getId());
        return saved;
    }

    @Override
    public MessageTemplate update(UUID id, String name, MessageType messageType, String content, String icon) {
        Optional<MessageTemplate> existingOpt = messageTemplateRepository.findById(id);
        if (existingOpt.isEmpty()) {
            throw new IllegalArgumentException("Message template not found: " + id);
        }

        MessageTemplate template = existingOpt.get();
        template.setName(name);
        template.setMessageType(messageType);
        template.setContent(content);
        template.setIcon(icon);
        template.setUpdatedAt(Instant.now());

        MessageTemplate saved = messageTemplateRepository.save(template);
        LOG.infof("Updated message template: %s", saved.getId());
        return saved;
    }

    @Override
    public void delete(UUID id) {
        messageTemplateRepository.delete(id);
        LOG.infof("Deleted message template: %s", id);
    }
}
