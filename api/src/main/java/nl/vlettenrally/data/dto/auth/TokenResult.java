package nl.vlettenrally.data.dto.auth;

import java.util.Set;

public record TokenResult(
    String accessToken,
    String refreshToken,
    long expiresIn,
    String subject,
    String name,
    String role,
    Set<String> roles
) {
    // Backward compatibility constructor for single role
    public TokenResult(String accessToken, String refreshToken, long expiresIn, String subject, String name, String role) {
        this(accessToken, refreshToken, expiresIn, subject, name, role, Set.of(role));
    }

    // Constructor for multiple roles
    public TokenResult(String accessToken, String refreshToken, long expiresIn, String subject, String name, Set<String> roles) {
        this(accessToken, refreshToken, expiresIn, subject, name, String.join(",", roles), roles);
    }
}
