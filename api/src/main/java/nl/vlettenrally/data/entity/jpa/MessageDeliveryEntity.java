package nl.vlettenrally.data.entity.jpa;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import nl.vlettenrally.data.enums.TransmissionChannel;

import java.time.Instant;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/**
 * JPA entity for MessageDelivery
 */
@Setter
@Getter
@Entity
@Table(name = "message_deliveries", indexes = {
    @Index(name = "idx_message_deliveries_message_id", columnList = "message_id"),
    @Index(name = "idx_message_deliveries_team_id", columnList = "team_id"),
    @Index(name = "idx_message_deliveries_delivered", columnList = "delivered")
}, uniqueConstraints = {
    @UniqueConstraint(name = "uq_message_team_delivery", columnNames = {"message_id", "team_id"})
})
public class MessageDeliveryEntity {

    // Getters and Setters
    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "message_id", nullable = false)
    private MessageEntity message;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "team_id", nullable = false)
    private TeamEntity team;

    @ElementCollection(targetClass = TransmissionChannel.class, fetch = FetchType.LAZY)
    @CollectionTable(
        name = "message_delivery_channels",
        joinColumns = @JoinColumn(name = "message_delivery_id")
    )
    @Enumerated(EnumType.STRING)
    @Column(name = "channel", length = 20)
    private Set<TransmissionChannel> transmittedVia = new HashSet<>();

    @Column(name = "delivered", nullable = false)
    private boolean delivered = false;

    @Column(name = "transmission_error", columnDefinition = "TEXT")
    private String transmissionError;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    public MessageDeliveryEntity() {
        this.id = UUID.randomUUID();
        this.createdAt = Instant.now();
        this.updatedAt = Instant.now();
    }

    @PreUpdate
    protected void onUpdate() {
        this.updatedAt = Instant.now();
    }

    /**
     * Add a transmission channel to the set.
     */
    public void addTransmissionChannel(TransmissionChannel channel) {
        if (this.transmittedVia == null) {
            this.transmittedVia = new HashSet<>();
        }
        this.transmittedVia.add(channel);
    }

}
