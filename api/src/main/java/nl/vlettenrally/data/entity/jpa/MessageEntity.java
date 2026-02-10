package nl.vlettenrally.data.entity.jpa;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
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
@Setter
@Getter
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

    // Getters and Setters
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

}
