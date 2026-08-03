package nl.vlettenrally.data.domain;

import java.time.Instant;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

import lombok.Getter;
import lombok.Setter;
import nl.vlettenrally.data.enums.MessageSource;
import nl.vlettenrally.data.enums.MessageType;
import nl.vlettenrally.data.enums.TransmissionChannel;

/**
 * Message entity representing a message sent to a team
 */
@Setter
@Getter
public class Message {
    // Getters and Setters
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
    /**
     * The rally desk asked for this message to reach a phone, so SMS is sent whether or not the
     * team's devices are active. Persisted rather than derived so a retry escalates the same way.
     */
    private boolean sendViaSms;


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
