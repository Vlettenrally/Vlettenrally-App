package nl.vlettenrally.data.repository.mock;

import io.quarkus.arc.profile.IfBuildProfile;
import jakarta.annotation.PostConstruct;
import jakarta.enterprise.context.ApplicationScoped;
import nl.vlettenrally.data.domain.RefreshToken;
import nl.vlettenrally.data.repository.RefreshTokenRepository;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * Mock implementation of RefreshTokenRepository using in-memory storage.
 * Only active in dev and test profiles - replaced by JpaRefreshTokenRepository in prod.
 * <p>
 * Note for test authors: every method hands back the <em>live</em> stored instance, and
 * {@link #save} stores that same instance. So mutating a fetched domain object has already
 * mutated the repository, and {@link #findByIdFresh} can never diverge from {@link #findById}
 * here. That means this mock cannot reproduce JPA first-level-cache staleness — the failure
 * mode findByIdFresh exists for is only observable against a real database.
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
    public Optional<RefreshToken> findByIdFresh(UUID id) {
        return findById(id);
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
    public Optional<RefreshToken> findActiveByFamily(UUID familyId) {
        Instant now = Instant.now();
        // Same ordering and filtering as the JPA implementation: newest first, revoked and
        // expired rows excluded. A divergence here would make the replay tests meaningless.
        return tokensById.values().stream()
                .filter(t -> familyId.equals(t.getFamilyId()))
                .filter(t -> !t.isRevoked())
                .filter(t -> t.getExpiresAt().isAfter(now))
                .max(Comparator.comparing(RefreshToken::getCreatedAt));
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
    public void deleteByFamily(UUID familyId) {
        removeAll(tokensById.values().stream()
                .filter(t -> familyId.equals(t.getFamilyId()))
                .toList());
    }

    @Override
    public void deleteBySubjectAndDevice(String subject, UUID deviceId) {
        removeAll(tokensById.values().stream()
                .filter(t -> t.getSubject().equals(subject) && deviceId.equals(t.getDeviceId()))
                .toList());
    }

    private void removeAll(List<RefreshToken> tokens) {
        for (RefreshToken token : tokens) {
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
    public synchronized int incrementReplayCount(UUID id) {
        RefreshToken token = tokensById.get(id);
        if (token == null) {
            return 0;
        }
        token.setReplayCount(token.getReplayCount() + 1);
        return token.getReplayCount();
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
