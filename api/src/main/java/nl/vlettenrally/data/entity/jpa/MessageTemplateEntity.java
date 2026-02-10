package nl.vlettenrally.data.entity.jpa;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import nl.vlettenrally.data.enums.MessageType;

import java.time.Instant;
import java.util.UUID;

/**
 * JPA entity for MessageTemplate
 */
@Setter
@Getter
@Entity
@Table(name = "message_templates", indexes = {
    @Index(name = "idx_message_templates_message_type", columnList = "message_type")
})
public class MessageTemplateEntity {

    // Getters and Setters
    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "name", nullable = false)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(name = "message_type", nullable = false, length = 20)
    private MessageType messageType;

    @Column(name = "content", nullable = false, columnDefinition = "TEXT")
    private String content;

    @Column(name = "icon", length = 100)
    private String icon;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    public MessageTemplateEntity() {
        this.id = UUID.randomUUID();
        this.createdAt = Instant.now();
        this.updatedAt = Instant.now();
    }

    @PreUpdate
    protected void onUpdate() {
        this.updatedAt = Instant.now();
    }

}
