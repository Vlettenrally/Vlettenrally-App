package nl.vlettenrally.data.domain;

import java.time.Instant;
import java.util.UUID;

import lombok.Getter;
import lombok.Setter;
import nl.vlettenrally.data.enums.MessageType;

/**
 * MessageTemplate entity representing a reusable message template.
 * Templates are global (not game-scoped) and can be used by admins
 * to quickly send pre-defined messages to teams.
 */
@Setter
@Getter
public class MessageTemplate {
    // Getters and Setters
    private UUID id;
    private String name;
    private MessageType messageType;
    private String content;
    private String icon;
    private Instant createdAt;
    private Instant updatedAt;

    public MessageTemplate() {
        this.id = UUID.randomUUID();
        this.createdAt = Instant.now();
        this.updatedAt = Instant.now();
    }

    public MessageTemplate(UUID id, String name, MessageType messageType, String content, String icon) {
        this();
        this.id = id;
        this.name = name;
        this.messageType = messageType;
        this.content = content;
        this.icon = icon;
    }

}
