package nl.vlettenrally.data.domain;

import java.time.Instant;
import java.util.UUID;
import nl.vlettenrally.data.enums.MessageType;

/**
 * MessageTemplate entity representing a reusable message template.
 * Templates are global (not game-scoped) and can be used by admins
 * to quickly send pre-defined messages to teams.
 */
public class MessageTemplate {
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

    // Getters and Setters
    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public MessageType getMessageType() { return messageType; }
    public void setMessageType(MessageType messageType) { this.messageType = messageType; }

    public String getContent() { return content; }
    public void setContent(String content) { this.content = content; }

    public String getIcon() { return icon; }
    public void setIcon(String icon) { this.icon = icon; }

    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }

    public Instant getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }
}
