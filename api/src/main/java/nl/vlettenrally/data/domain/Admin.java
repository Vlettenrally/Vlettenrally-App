package nl.vlettenrally.data.domain;

import nl.vlettenrally.data.enums.Role;

import java.time.Instant;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/**
 * Admin entity representing an administrator user
 */
public class Admin {
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

    // Getters and Setters
    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }

    public String getUsername() { return username; }
    public void setUsername(String username) { this.username = username; }

    public String getPasswordHash() { return passwordHash; }
    public void setPasswordHash(String passwordHash) { this.passwordHash = passwordHash; }

    public Set<Role> getRoles() { return roles; }
    public void setRoles(Set<Role> roles) { this.roles = roles; }

    public boolean isActive() { return active; }
    public void setActive(boolean active) { this.active = active; }

    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }

    public Instant getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }
}
