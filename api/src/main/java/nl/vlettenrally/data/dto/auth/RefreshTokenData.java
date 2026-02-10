package nl.vlettenrally.data.dto.auth;

import java.util.Set;

public record RefreshTokenData(
    String subject,
    String name,
    String role,
    Set<String> roles,
    long expiresAt,
    boolean revoked
) {
    // Backward compatibility constructor for single role
    public RefreshTokenData(String subject, String name, String role, long expiresAt, boolean revoked) {
        this(subject, name, role, Set.of(role), expiresAt, revoked);

    }
}
