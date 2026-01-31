package nl.vlettenrally.data.domain;

import java.time.Instant;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import nl.vlettenrally.data.enums.TransmissionChannel;

/**
 * MessageDelivery entity tracking per-team delivery status for a message.
 * This enables granular retry of only failed deliveries.
 */
public class MessageDelivery {
    private UUID id;
    private UUID messageId;
    private UUID teamId;
    private Set<TransmissionChannel> transmittedVia;
    private boolean delivered;
    private String transmissionError;
    private Instant createdAt;
    private Instant updatedAt;

    public MessageDelivery() {
        this.id = UUID.randomUUID();
        this.transmittedVia = new HashSet<>();
        this.delivered = false;
        this.createdAt = Instant.now();
        this.updatedAt = Instant.now();
    }

    public MessageDelivery(UUID messageId, UUID teamId) {
        this();
        this.messageId = messageId;
        this.teamId = teamId;
    }

    // Getters and Setters
    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }

    public UUID getMessageId() { return messageId; }
    public void setMessageId(UUID messageId) { this.messageId = messageId; }

    public UUID getTeamId() { return teamId; }
    public void setTeamId(UUID teamId) { this.teamId = teamId; }

    public Set<TransmissionChannel> getTransmittedVia() { return transmittedVia; }
    public void setTransmittedVia(Set<TransmissionChannel> transmittedVia) { this.transmittedVia = transmittedVia; }

    public boolean isDelivered() { return delivered; }
    public void setDelivered(boolean delivered) { this.delivered = delivered; }

    public String getTransmissionError() { return transmissionError; }
    public void setTransmissionError(String transmissionError) { this.transmissionError = transmissionError; }

    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }

    public Instant getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }

    /**
     * Add a transmission channel to the set of channels this delivery was transmitted via.
     */
    public void addTransmissionChannel(TransmissionChannel channel) {
        if (this.transmittedVia == null) {
            this.transmittedVia = new HashSet<>();
        }
        this.transmittedVia.add(channel);
    }
}
