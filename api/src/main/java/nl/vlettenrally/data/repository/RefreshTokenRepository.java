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
     * Delete all expired tokens (cleanup)
     */
    void deleteExpiredTokens(Instant now);
}
