package nl.vlettenrally.data.repository.mock;

import io.quarkus.arc.profile.IfBuildProfile;
import jakarta.annotation.PostConstruct;
import jakarta.enterprise.context.ApplicationScoped;
import nl.vlettenrally.data.domain.RefreshToken;
import nl.vlettenrally.data.repository.RefreshTokenRepository;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * Mock implementation of RefreshTokenRepository using in-memory storage.
 * Only active in dev and test profiles - replaced by JpaRefreshTokenRepository in prod.
 */
@IfBuildProfile(anyOf = {"dev", "test"})
@ApplicationScoped
public class MockRefreshTokenRepository implements RefreshTokenRepository {

    private final Map<UUID, RefreshToken> tokensById = new ConcurrentHashMap<>();
    private final Map<String, RefreshToken> tokensByToken = new ConcurrentHashMap<>();

    @PostConstruct
    void init() {
        initializeData();
    }

    public void initializeData() {
        tokensById.clear();
        tokensByToken.clear();
    }

    @Override
    public Optional<RefreshToken> findById(UUID id) {
        return Optional.ofNullable(tokensById.get(id));
    }

    @Override
    public Optional<RefreshToken> findByToken(String token) {
        return Optional.ofNullable(tokensByToken.get(token));
    }

    @Override
    public List<RefreshToken> findBySubject(String subject) {
        return tokensById.values().stream()
                .filter(t -> t.getSubject().equals(subject))
                .collect(Collectors.toList());
    }

    @Override
    public List<RefreshToken> findAll() {
        return new ArrayList<>(tokensById.values());
    }

    @Override
    public RefreshToken save(RefreshToken refreshToken) {
        if (refreshToken.getId() == null) {
            refreshToken.setId(UUID.randomUUID());
        }
        
        // Remove old token entry if token string changed
        RefreshToken existing = tokensById.get(refreshToken.getId());
        if (existing != null && !existing.getToken().equals(refreshToken.getToken())) {
            tokensByToken.remove(existing.getToken());
        }
        
        tokensById.put(refreshToken.getId(), refreshToken);
        tokensByToken.put(refreshToken.getToken(), refreshToken);
        return refreshToken;
    }

    @Override
    public void delete(UUID id) {
        RefreshToken token = tokensById.remove(id);
        if (token != null) {
            tokensByToken.remove(token.getToken());
        }
    }

    @Override
    public void deleteByToken(String token) {
        RefreshToken refreshToken = tokensByToken.remove(token);
        if (refreshToken != null) {
            tokensById.remove(refreshToken.getId());
        }
    }

    @Override
    public void deleteBySubject(String subject) {
        List<RefreshToken> toDelete = tokensById.values().stream()
                .filter(t -> t.getSubject().equals(subject))
                .toList();
        
        for (RefreshToken token : toDelete) {
            tokensById.remove(token.getId());
            tokensByToken.remove(token.getToken());
        }
    }

    @Override
    public synchronized int markRevokedIfActive(UUID id, Instant revokedAt, String replacedByToken) {
        RefreshToken token = tokensById.get(id);
        if (token == null || token.isRevoked()) {
            return 0;
        }
        token.setRevoked(true);
        token.setRevokedAt(revokedAt);
        token.setReplacedByToken(replacedByToken);
        return 1;
    }

    @Override
    public void deleteExpiredTokens(Instant now) {
        List<RefreshToken> toDelete = tokensById.values().stream()
                .filter(t -> t.getExpiresAt().isBefore(now))
                .toList();
        
        for (RefreshToken token : toDelete) {
            tokensById.remove(token.getId());
            tokensByToken.remove(token.getToken());
        }
    }
}
