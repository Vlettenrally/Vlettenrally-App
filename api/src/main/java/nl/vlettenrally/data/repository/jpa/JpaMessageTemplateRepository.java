package nl.vlettenrally.data.repository.jpa;

import io.quarkus.arc.profile.IfBuildProfile;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.transaction.Transactional;
import nl.vlettenrally.data.domain.MessageTemplate;
import nl.vlettenrally.data.entity.jpa.MessageTemplateEntity;
import nl.vlettenrally.data.mapper.MessageTemplateMapper;
import nl.vlettenrally.data.repository.MessageTemplateRepository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * JPA implementation of MessageTemplateRepository with read replica support.
 * Only active in prod profile - replaced by MockMessageTemplateRepository in dev/test.
 */
@IfBuildProfile("prod")
@ApplicationScoped
public class JpaMessageTemplateRepository extends ReadReplicaAwareRepository implements MessageTemplateRepository {

    @Override
    public Optional<MessageTemplate> findById(UUID id) {
        MessageTemplateEntity entity = getReadEntityManager().find(MessageTemplateEntity.class, id);
        return Optional.ofNullable(MessageTemplateMapper.toDomain(entity));
    }

    @Override
    public List<MessageTemplate> findAll() {
        List<MessageTemplateEntity> entities = getReadEntityManager()
                .createQuery("SELECT t FROM MessageTemplateEntity t ORDER BY t.name", MessageTemplateEntity.class)
                .getResultList();
        return entities.stream()
                .map(MessageTemplateMapper::toDomain)
                .collect(Collectors.toList());
    }

    @Override
    @Transactional
    public MessageTemplate save(MessageTemplate template) {
        MessageTemplateEntity entity = getWriteEntityManager().find(MessageTemplateEntity.class, template.getId());
        
        if (entity == null) {
            // New entity
            entity = MessageTemplateMapper.toEntity(template);
            getWriteEntityManager().persist(entity);
        } else {
            // Update existing
            MessageTemplateMapper.updateEntity(entity, template);
            entity.setUpdatedAt(Instant.now());
            entity = getWriteEntityManager().merge(entity);
        }
        
        return MessageTemplateMapper.toDomain(entity);
    }

    @Override
    @Transactional
    public void delete(UUID id) {
        MessageTemplateEntity entity = getWriteEntityManager().find(MessageTemplateEntity.class, id);
        if (entity != null) {
            getWriteEntityManager().remove(entity);
        }
    }
}
