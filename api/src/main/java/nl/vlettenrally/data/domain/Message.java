package nl.vlettenrally.data.domain;

import java.time.Instant;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import nl.vlettenrally.data.enums.MessageSource;
import nl.vlettenrally.data.enums.MessageType;
import nl.vlettenrally.data.enums.TransmissionChannel;

/**
 * Message entity representing a message sent to a team
 */
public class Message {
    private UUID id;
    private UUID gameId;
    private UUID teamId;
    private MessageType messageType;
    private String content;
    private String icon;
    private Instant sentAt;
    private boolean delivered;
    private boolean read;
    private Set<TransmissionChannel> transmittedVia;
    private String transmissionError;
    private MessageSource source;
    private UUID sentByAdminId;
    /** Groups messages from the same admin broadcast */
    private UUID batchId;


    public Message() {
        this.id = UUID.randomUUID();
        this.sentAt = Instant.now();
        this.delivered = true;
        this.read = false;
        this.transmittedVia = new HashSet<>();
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

    public Set<TransmissionChannel> getTransmittedVia() { return transmittedVia; }
    public void setTransmittedVia(Set<TransmissionChannel> transmittedVia) { this.transmittedVia = transmittedVia; }

    public String getTransmissionError() { return transmissionError; }
    public void setTransmissionError(String transmissionError) { this.transmissionError = transmissionError; }

    public UUID getGameId() { return gameId; }
    public void setGameId(UUID gameId) { this.gameId = gameId; }

    public MessageSource getSource() { return source; }
    public void setSource(MessageSource source) { this.source = source; }

    public UUID getSentByAdminId() { return sentByAdminId; }
    public void setSentByAdminId(UUID sentByAdminId) { this.sentByAdminId = sentByAdminId; }

    public UUID getBatchId() { return batchId; }
    public void setBatchId(UUID batchId) { this.batchId = batchId; }

    /**
     * Add a transmission channel to the set of channels this message was transmitted via.
     */
    public void addTransmissionChannel(TransmissionChannel channel) {
        if (this.transmittedVia == null) {
            this.transmittedVia = new HashSet<>();
        }
        this.transmittedVia.add(channel);
    }
}
