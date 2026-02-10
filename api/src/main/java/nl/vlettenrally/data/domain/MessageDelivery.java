package nl.vlettenrally.data.domain;

import java.time.Instant;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

import lombok.Getter;
import lombok.Setter;
import nl.vlettenrally.data.enums.TransmissionChannel;

/**
 * MessageDelivery entity tracking per-team delivery status for a message.
 * This enables granular retry of only failed deliveries.
 */
@Setter
@Getter
public class MessageDelivery {
    // Getters and Setters
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
