package nl.vlettenrally.data.entity.jpa;

import jakarta.persistence.*;
import nl.vlettenrally.data.enums.MessageSource;
import nl.vlettenrally.data.enums.MessageType;
import nl.vlettenrally.data.enums.TransmissionChannel;

import java.time.Instant;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/**
 * JPA entity for Message
 */
@Entity
@Table(name = "messages", indexes = {
    @Index(name = "idx_messages_team_id", columnList = "team_id"),
    @Index(name = "idx_messages_game_id", columnList = "game_id"),
    @Index(name = "idx_messages_batch_id", columnList = "batch_id"),
    @Index(name = "idx_messages_sent_at", columnList = "sent_at"),
    @Index(name = "idx_messages_game_source", columnList = "game_id, source"),
    @Index(name = "idx_messages_delivered", columnList = "delivered")
})
public class MessageEntity {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "game_id", nullable = false)
    private GameEntity game;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "team_id", nullable = false)
    private TeamEntity team;

    @Enumerated(EnumType.STRING)
    @Column(name = "message_type", nullable = false, length = 20)
    private MessageType messageType;

    @Column(name = "content", columnDefinition = "TEXT")
    private String content;

    @Column(name = "icon", length = 100)
    private String icon;

    @Column(name = "sent_at", nullable = false)
    private Instant sentAt;

    @Column(name = "delivered", nullable = false)
    private boolean delivered = true;

    @Column(name = "read", nullable = false)
    private boolean read = false;

    @ElementCollection(targetClass = TransmissionChannel.class, fetch = FetchType.LAZY)
    @CollectionTable(
        name = "message_transmission_channels",
        joinColumns = @JoinColumn(name = "message_id")
    )
    @Enumerated(EnumType.STRING)
    @Column(name = "channel", length = 20)
    private Set<TransmissionChannel> transmittedVia = new HashSet<>();

    @Column(name = "transmission_error", columnDefinition = "TEXT")
    private String transmissionError;

    @Enumerated(EnumType.STRING)
    @Column(name = "source", length = 20)
    private MessageSource source;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "sent_by_admin_id")
    private AdminEntity sentByAdmin;

    @Column(name = "batch_id")
    private UUID batchId;

    public MessageEntity() {
        this.id = UUID.randomUUID();
        this.sentAt = Instant.now();
    }

    // Getters and Setters
    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }

    public GameEntity getGame() { return game; }
    public void setGame(GameEntity game) { this.game = game; }

    public TeamEntity getTeam() { return team; }
    public void setTeam(TeamEntity team) { this.team = team; }

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

    public MessageSource getSource() { return source; }
    public void setSource(MessageSource source) { this.source = source; }

    public AdminEntity getSentByAdmin() { return sentByAdmin; }
    public void setSentByAdmin(AdminEntity sentByAdmin) { this.sentByAdmin = sentByAdmin; }

    public UUID getBatchId() { return batchId; }
    public void setBatchId(UUID batchId) { this.batchId = batchId; }
}
