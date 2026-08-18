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
    private Instant revokedAt;
    private String replacedByToken;
    /**
     * The rotation lineage this token belongs to: one login on one device, however many times it
     * has rotated since. Revocation is scoped to a family so that one device's mistake cannot log
     * out the rest of a team, which all share the same {@code subject}.
     */
    private UUID familyId;
    /** The device this lineage was issued to, when known. Null for admins and legacy rows. */
    private UUID deviceId;
    /**
     * How many times this token has been re-presented after it was rotated away and resolved back
     * to its lineage's live head. One or two is a client that lost a rotation response; a client
     * that keeps presenting the same superseded token is not storing what it is given.
     */
    private int replayCount;
    private Instant createdAt;
    private Instant updatedAt;

    public RefreshToken() {
        this.id = UUID.randomUUID();
        this.revoked = false;
        // Safety net: a row that nobody assigns a family to is the root of its own.
        this.familyId = this.id;
        this.createdAt = Instant.now();
        this.updatedAt = Instant.now();
    }

    public RefreshToken(UUID id, String token, String subject, String name, Set<String> roles, Instant expiresAt, boolean revoked) {
        this();
        this.id = id;
        this.familyId = id;
        this.token = token;
        this.subject = subject;
        this.name = name;
        this.roles = roles;
        this.expiresAt = expiresAt;
        this.revoked = revoked;
    }
}
