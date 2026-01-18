package nl.vlettenrally.data.domain;

import java.time.Instant;
import java.util.UUID;

/**
 * Message entity representing a message sent to a team
 */
public class Message {
    private UUID id;
    private UUID teamId;
    private MessageType messageType;
    private String content;
    private String icon;
    private Instant sentAt;
    private boolean delivered;
    private boolean read;

    public enum MessageType {
        GENERAL,
        COUNTDOWN,
        CHECKPOINT,
        RALLYDESK,
        ACHIEVEMENT,
        RIVAL
    }

    public Message() {
        this.id = UUID.randomUUID();
        this.sentAt = Instant.now();
        this.delivered = true;
        this.read = false;
    }

    public Message(UUID id, UUID teamId, MessageType messageType, String content, Instant sentAt) {
        this();
        this.id = id;
        this.teamId = teamId;
        this.messageType = messageType;
        this.content = content;
        this.sentAt = sentAt;
    }

    // Getters and Setters
    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }

    public UUID getTeamId() { return teamId; }
    public void setTeamId(UUID teamId) { this.teamId = teamId; }

    public MessageType getMessageType() { return messageType; }
    public void setMessageType(MessageType messageType) { this.messageType = messageType; }

    public String getContent() { return content; }
    public void setContent(String content) { this.content = content; }

    public String getIcon() { return icon; }
    public void setIcon(String icon) { this.icon = icon; }

    public Instant getSentAt() { return sentAt; }
    public void setSentAt(Instant sentAt) { this.sentAt = sentAt; }

    public boolean isDelivered() { return delivered; }
    public void setDelivered(boolean delivered) { this.delivered = delivered; }

    public boolean isRead() { return read; }
    public void setRead(boolean read) { this.read = read; }
}
