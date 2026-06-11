package nl.vlettenrally.data.domain;

import lombok.Getter;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

/**
 * Persistent inbox notification for admin users.
 */
@Getter
@Setter
public class AdminNotification {
    private UUID id;
    private UUID gameId;
    private UUID teamId;
    private String source;
    private String content;
    private String metadataJson;
    private Instant createdAt;
    private boolean read;
    private Instant readAt;
    private UUID readByAdminId;

    public AdminNotification() {
        this.id = UUID.randomUUID();
        this.createdAt = Instant.now();
        this.read = false;
    }
}
