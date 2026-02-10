package nl.vlettenrally.data.domain;

import jakarta.persistence.Column;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;
import java.util.Set;
import java.util.UUID;

/**
 * RefreshToken entity representing a refresh token for authentication.
 * Stored in database for persistence across server restarts and distributed deployments.
 */
@Getter
@Setter
public class RefreshToken {
    private UUID id;
    private String token;
    private String subject;
    private String name;
    @Column(columnDefinition = "text[]")
    private Set<String> roles;
    private Instant expiresAt;
    private boolean revoked;
    private Instant createdAt;
    private Instant updatedAt;

    public RefreshToken() {
        this.id = UUID.randomUUID();
        this.revoked = false;
        this.createdAt = Instant.now();
        this.updatedAt = Instant.now();
    }

    public RefreshToken(UUID id, String token, String subject, String name, Set<String> roles, Instant expiresAt, boolean revoked) {
        this();
        this.id = id;
        this.token = token;
        this.subject = subject;
        this.name = name;
        this.roles = roles;
        this.expiresAt = expiresAt;
        this.revoked = revoked;
    }
}
