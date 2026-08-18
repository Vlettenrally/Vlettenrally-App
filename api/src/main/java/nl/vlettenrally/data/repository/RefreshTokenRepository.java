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

    /**
     * Re-read a token straight from the database, bypassing any first-level cache.
     * <p>
     * Needed after {@link #markRevokedIfActive} loses the rotation race: that is a bulk UPDATE,
     * which by design does not refresh instances already loaded in the persistence context, so a
     * plain {@link #findById} would hand back a stale row (revoked=false, no successor) and the
     * caller would mistake a concurrent rotation for a replay attack.
     */
    Optional<RefreshToken> findByIdFresh(UUID id);

    Optional<RefreshToken> findByToken(String token);
    
    List<RefreshToken> findBySubject(String subject);

    /**
     * The still-usable head of a rotation family: the newest row that is neither revoked nor
     * expired. Empty when the whole family has been rotated past, revoked or aged out.
     */
    Optional<RefreshToken> findActiveByFamily(UUID familyId);

    List<RefreshToken> findAll();
    
    RefreshToken save(RefreshToken refreshToken);
    
    void delete(UUID id);
    
    void deleteByToken(String token);

    void deleteBySubject(String subject);

    /**
     * Delete one rotation lineage. This is the blast radius of the replay tripwire: a team's other
     * devices live under the same subject and must survive.
     */
    void deleteByFamily(UUID familyId);

    /** Delete the refresh tokens of a single device, leaving the team's other devices alone. */
    void deleteBySubjectAndDevice(String subject, UUID deviceId);

    /**
     * Atomically mark a still-active token as revoked, recording the successor token
     * that replaces it. Returns the number of rows affected: 1 if this caller won the
     * rotation, 0 if the token was already revoked (concurrent rotation or replay).
     * This makes refresh-token rotation race-safe without a separate lock.
     */
    int markRevokedIfActive(UUID id, Instant revokedAt, String replacedByToken);

    /**
     * Record that a superseded token was presented again and resolved back to its lineage's live
     * head, returning the new count. Separate from {@link #save} so the increment is a single
     * atomic statement and cannot be lost to a concurrent write of the same row.
     */
    int incrementReplayCount(UUID id);

    /**
     * Delete all expired tokens (cleanup)
     */
    void deleteExpiredTokens(Instant now);
}
