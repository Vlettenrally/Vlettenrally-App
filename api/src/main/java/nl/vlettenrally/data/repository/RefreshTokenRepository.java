package nl.vlettenrally.data.repository;

import nl.vlettenrally.data.domain.RefreshToken;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Repository interface for RefreshToken entities
 */
public interface RefreshTokenRepository {
    
    Optional<RefreshToken> findById(UUID id);
    
    Optional<RefreshToken> findByToken(String token);
    
    List<RefreshToken> findBySubject(String subject);
    
    List<RefreshToken> findAll();
    
    RefreshToken save(RefreshToken refreshToken);
    
    void delete(UUID id);
    
    void deleteByToken(String token);

    void deleteBySubject(String subject);

    /**
     * Atomically mark a still-active token as revoked, recording the successor token
     * that replaces it. Returns the number of rows affected: 1 if this caller won the
     * rotation, 0 if the token was already revoked (concurrent rotation or replay).
     * This makes refresh-token rotation race-safe without a separate lock.
     */
    int markRevokedIfActive(UUID id, Instant revokedAt, String replacedByToken);

    /**
     * Delete all expired tokens (cleanup)
     */
    void deleteExpiredTokens(Instant now);
}
