package nl.vlettenrally.data.domain;

import lombok.Getter;
import lombok.Setter;
import nl.vlettenrally.data.enums.Role;

import java.time.Instant;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/**
 * Admin entity representing an administrator user
 */
@Setter
@Getter
public class Admin {
    // Getters and Setters
    private UUID id;
    private String username;
    private String passwordHash;
    private Set<Role> roles;
    private boolean active;
    private Instant createdAt;
    private Instant updatedAt;

    public Admin() {
        this.id = UUID.randomUUID();
        this.roles = new HashSet<>();
        this.active = true;
        this.createdAt = Instant.now();
        this.updatedAt = Instant.now();
    }

    public Admin(UUID id, String username, String passwordHash, Set<Role> roles) {
        this();
        this.id = id;
        this.username = username;
        this.passwordHash = passwordHash;
        this.roles = roles != null ? new HashSet<>(roles) : new HashSet<>();
    }

}
