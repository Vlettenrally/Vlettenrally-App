package nl.vlettenrally.service.impl;

import io.smallrye.jwt.auth.principal.JWTParser;
import io.smallrye.jwt.build.Jwt;
import io.smallrye.mutiny.Uni;
import io.quarkus.logging.Log;
import io.quarkus.elytron.security.common.BcryptUtil;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import nl.vlettenrally.data.domain.Admin;
import nl.vlettenrally.data.enums.Role;
import nl.vlettenrally.data.enums.TokenType;
import nl.vlettenrally.data.domain.Game;
import nl.vlettenrally.data.domain.Team;
import nl.vlettenrally.data.repository.AdminRepository;
import nl.vlettenrally.data.repository.GameRepository;
import nl.vlettenrally.data.repository.TeamRepository;
import nl.vlettenrally.service.AuthService;
import nl.vlettenrally.service.InviteCodeStore;
import nl.vlettenrally.service.TokenRevocationStore;
import org.eclipse.microprofile.config.inject.ConfigProperty;

import java.time.Instant;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;
import nl.vlettenrally.data.dto.auth.RegistrationTokenData;
import nl.vlettenrally.data.dto.auth.TeamInvite;
import nl.vlettenrally.data.dto.auth.TeamInviteLinkResponse;
import nl.vlettenrally.data.dto.auth.TokenResult;
import nl.vlettenrally.data.domain.RefreshToken;
import nl.vlettenrally.data.repository.RefreshTokenRepository;

/**
 * Implementation of AuthService
 * Refresh tokens are persisted to database for production use.
 * Registration tokens use in-memory storage (short-lived, single-use).
 */
@ApplicationScoped
public class AuthServiceImpl implements AuthService {

    private static final int REFRESH_TOKEN_BYTE_LENGTH = 32;
    private static final class SecureRandomHolder {
        static final SecureRandom INSTANCE = new SecureRandom();
    }

    @Inject
    TeamRepository teamRepository;

    @Inject
    GameRepository gameRepository;

    @Inject
    RefreshTokenRepository refreshTokenRepository;

    @Inject
    AdminRepository adminRepository;

    @Inject
    TokenRevocationStore tokenRevocationStore;

    @ConfigProperty(name = "mp.jwt.verify.issuer", defaultValue = "vlettenrally")
    String issuer;


    @ConfigProperty(name = "mp.jwt.verify.audience", defaultValue = "vlettenrally-audience")
    String audience;

    @ConfigProperty(name = "vlettenrally.jwt.access-token-expiry-minutes", defaultValue = "15")
    int accessTokenExpiryMinutes;

    @ConfigProperty(name = "vlettenrally.jwt.refresh-token-expiry-days", defaultValue = "1")
    int refreshTokenExpiryDays;

    @ConfigProperty(name = "vlettenrally.jwt.refresh-token-reuse-grace-seconds", defaultValue = "30")
    int refreshTokenReuseGraceSeconds;

    // How long after rotation a client may re-present the old refresh token and still get the
    // live successor back (idempotent replay), rather than triggering a family-wide revocation.
    // Covers the common "refresh response lost on a flaky mobile network" case.
    @ConfigProperty(name = "vlettenrally.jwt.refresh-token-replay-window-seconds", defaultValue = "300")
    int refreshTokenReplayWindowSeconds;

    @ConfigProperty(name = "vlettenrally.frontend.base-url", defaultValue = "http://localhost:5173")
    String frontendBaseUrl;

    @Inject
    JWTParser jwtParser;

    @Inject
    InviteCodeStore inviteCodeStore;

    // In-memory storage for registration tokens (short-lived, can remain in-memory)
    private final Map<String, RegistrationTokenData> registrationTokens = new ConcurrentHashMap<>();

    @Override
    public Optional<TokenResult> exchangeToken(String registrationToken) {
        RegistrationTokenData tokenData = registrationTokens.get(registrationToken);

        if (tokenData == null) {
            return Optional.empty();
        }

        if (tokenData.used()) {
            return Optional.empty();
        }

        Instant now = Instant.now();

        if (now.getEpochSecond() > tokenData.expiresAt()) {
            registrationTokens.remove(registrationToken);
            return Optional.empty();
        }

        // Mark as used
        tokenData = new RegistrationTokenData(
            tokenData.subject(),
            tokenData.name(),
            tokenData.role(),
            tokenData.expiresAt(),
            true
        );
        registrationTokens.put(registrationToken, tokenData);

        // Generate tokens using config-driven durations
        long accessTokenExpirySeconds = accessTokenExpiryMinutes * 60L;
        long refreshTokenExpirySeconds = refreshTokenExpiryDays * 86400L;

        String accessToken = generateToken(tokenData.subject(), tokenData.name(), tokenData.role().toLowerCase(), Instant.now().plusSeconds(accessTokenExpirySeconds), TokenType.ACCESS);
        String refreshTokenStr = generateRefreshTokenValue();

        // Store refresh token in database for persistence
        RefreshToken refreshToken = new RefreshToken();
        refreshToken.setToken(refreshTokenStr);
        refreshToken.setSubject(tokenData.subject());
        refreshToken.setName(tokenData.name());
        refreshToken.setRoles(java.util.Collections.singleton(tokenData.role()));
        refreshToken.setExpiresAt(Instant.now().plusSeconds(refreshTokenExpirySeconds));
        refreshToken.setRevoked(false);
        refreshTokenRepository.save(refreshToken);

        // Remove used registration token
        registrationTokens.remove(registrationToken);

        Log.infof("Team tokens issued - teamId: %s, teamName: %s, role: %s",
            tokenData.subject(), tokenData.name(), tokenData.role());

        return Optional.of(new TokenResult(
            accessToken,
            refreshTokenStr,
            accessTokenExpirySeconds,
            tokenData.subject(),
            tokenData.name(),
            tokenData.role()

        ));
    }

    @Override
    @Transactional
    public Optional<TokenResult> refreshToken(String refreshTokenStr) {
        Optional<RefreshToken> tokenOpt = refreshTokenRepository.findByToken(refreshTokenStr);

        if (tokenOpt.isEmpty()) {
            return Optional.empty();
        }

        RefreshToken tokenData = tokenOpt.get();

        if (Instant.now().isAfter(tokenData.getExpiresAt())) {
            Log.warnf("Token refresh failed - expired token - subject: %s, name: %s",
                tokenData.getSubject(), tokenData.getName());
            refreshTokenRepository.delete(tokenData.getId());
            return Optional.empty();
        }

        // Resolve the subject's current identity (fresh roles/name from the DB). Needed both
        // for normal rotation and for minting a fresh access token on idempotent replay.
        UUID userId;
        try {
            userId = UUID.fromString(tokenData.getSubject());
        } catch (IllegalArgumentException e) {
            Log.warnf("Token refresh failed - invalid subject format - subject: %s", tokenData.getSubject());
            return Optional.empty();
        }

        ResolvedIdentity identity = resolveIdentity(userId);
        if (identity == null) {
            Log.warnf("Token refresh failed - user not found - userId: %s", userId);
            return Optional.empty();
        }

        long accessTokenExpirySeconds = accessTokenExpiryMinutes * 60L;
        long refreshTokenExpirySeconds = refreshTokenExpiryDays * 86400L;

        // Token was already rotated before we even looked -> concurrent refresh or a replayed
        // (possibly lost-response) token. Never nuke on the happy path; try idempotent replay.
        if (tokenData.isRevoked()) {
            return handleReplay(tokenData, identity, accessTokenExpirySeconds);
        }

        // Atomically claim the rotation. Exactly one concurrent caller flips revoked=false->true
        // and records the successor token; everyone else gets 0 rows and replays instead.
        String newRefreshTokenStr = generateRefreshTokenValue();
        int rowsWon = refreshTokenRepository.markRevokedIfActive(
            tokenData.getId(), Instant.now(), newRefreshTokenStr);

        if (rowsWon == 0) {
            // A concurrent request won the rotation between our read and update.
            RefreshToken reread = refreshTokenRepository.findById(tokenData.getId()).orElse(tokenData);
            return handleReplay(reread, identity, accessTokenExpirySeconds);
        }

        // We won the rotation: mint the new access token and persist the successor refresh token.
        String newAccessToken = mintAccessToken(identity, accessTokenExpirySeconds);

        RefreshToken newRefreshToken = new RefreshToken();
        newRefreshToken.setToken(newRefreshTokenStr);
        newRefreshToken.setSubject(userId.toString());
        newRefreshToken.setName(tokenData.getName()); // Keep original name for logging
        newRefreshToken.setRoles(identity.rolesForStorage);
        newRefreshToken.setExpiresAt(Instant.now().plusSeconds(refreshTokenExpirySeconds));
        newRefreshToken.setRevoked(false);
        refreshTokenRepository.save(newRefreshToken);

        Log.infof("Token refresh completed - subject: %s, name: %s, role(s): %s",
            userId, tokenData.getName(), identity.rolesForStorage);

        return Optional.of(new TokenResult(
            newAccessToken,
            newRefreshTokenStr,
            accessTokenExpirySeconds,
            userId.toString(),
            tokenData.getName(),
            String.join(",", identity.rolesForStorage)
        ));
    }

    /**
     * Handle a refresh request against an already-rotated (revoked) refresh token.
     *
     * <p>Within the replay window, if the token has a still-live successor, return that successor
     * plus a freshly minted access token. This makes refresh idempotent: a client that lost the
     * rotation response (common on flaky mobile networks) converges on the current live token
     * instead of locking the whole team out. Only a genuinely stale reuse — no live successor and
     * past the window — is treated as a replay attack and triggers a family-wide revocation.
     */
    private Optional<TokenResult> handleReplay(RefreshToken revokedToken, ResolvedIdentity identity,
                                               long accessTokenExpirySeconds) {
        Instant revokedAt = revokedToken.getRevokedAt();
        String successorTokenStr = revokedToken.getReplacedByToken();

        boolean withinReplayWindow = revokedAt == null
            || Instant.now().isBefore(revokedAt.plusSeconds(refreshTokenReplayWindowSeconds));

        if (withinReplayWindow && successorTokenStr != null) {
            Optional<RefreshToken> successorOpt = refreshTokenRepository.findByToken(successorTokenStr);
            if (successorOpt.isPresent()) {
                RefreshToken successor = successorOpt.get();
                if (!successor.isRevoked() && Instant.now().isBefore(successor.getExpiresAt())) {
                    String freshAccess = mintAccessToken(identity, accessTokenExpirySeconds);
                    Log.infof("Idempotent refresh replay - returning live successor token - subject: %s, name: %s",
                        revokedToken.getSubject(), revokedToken.getName());
                    return Optional.of(new TokenResult(
                        freshAccess,
                        successor.getToken(),
                        accessTokenExpirySeconds,
                        revokedToken.getSubject(),
                        revokedToken.getName(),
                        String.join(",", identity.rolesForStorage)
                    ));
                }
            }
        }

        // No usable successor but still within the short reuse grace: almost certainly a duplicate
        // in-flight request. Fail this one softly without revoking anything.
        if (revokedAt != null && Instant.now().isBefore(revokedAt.plusSeconds(refreshTokenReuseGraceSeconds))) {
            Log.warnf("Duplicate refresh token usage within grace period - subject: %s, name: %s, revokedAt: %s",
                revokedToken.getSubject(), revokedToken.getName(), revokedAt);
            return Optional.empty();
        }

        // Stale reuse of a long-revoked token with no live successor -> treat as a replay attack.
        Log.warnf("SECURITY: Replay attack detected - subject: %s, name: %s, revoking all tokens",
            revokedToken.getSubject(), revokedToken.getName());
        try {
            revokeTokens(UUID.fromString(revokedToken.getSubject()));
        } catch (IllegalArgumentException e) {
            refreshTokenRepository.deleteByToken(revokedToken.getToken());
        }
        return Optional.empty();
    }

    /**
     * The current identity of a refresh token's subject, resolved fresh from the DB so rotated
     * access tokens always carry up-to-date roles. Returns null if the subject no longer exists.
     */
    private ResolvedIdentity resolveIdentity(UUID userId) {
        Optional<Admin> adminOpt = adminRepository.findById(userId);
        if (adminOpt.isPresent()) {
            Admin admin = adminOpt.get();
            Set<Role> roles = admin.getRoles();
            Set<String> rolesForStorage = roles.stream()
                .map(Role::toString)
                .collect(Collectors.toSet());
            return new ResolvedIdentity(admin.getId().toString(), admin.getUsername(), roles, rolesForStorage);
        }

        Optional<Team> teamOpt = teamRepository.findById(userId);
        if (teamOpt.isPresent()) {
            Team team = teamOpt.get();
            return new ResolvedIdentity(team.getId().toString(), team.getName(),
                Set.of(Role.fromValue("team")), java.util.Collections.singleton("team"));
        }

        return null;
    }

    private String mintAccessToken(ResolvedIdentity identity, long accessTokenExpirySeconds) {
        return generateToken(identity.subjectId, identity.displayName, identity.roles,
            Instant.now().plusSeconds(accessTokenExpirySeconds), TokenType.ACCESS);
    }

    /** Immutable view of a refresh-token subject used to mint rotated access tokens. */
    private static final class ResolvedIdentity {
        final String subjectId;
        final String displayName;
        final Set<Role> roles;
        final Set<String> rolesForStorage;

        ResolvedIdentity(String subjectId, String displayName, Set<Role> roles, Set<String> rolesForStorage) {
            this.subjectId = subjectId;
            this.displayName = displayName;
            this.roles = roles;
            this.rolesForStorage = rolesForStorage;
        }
    }

    @Override
    public TokenResult issueTeamTokens(Team team) {
        return issueTeamTokens(team, null);
    }

    @Override
    public TokenResult issueTeamTokens(Team team, UUID deviceId) {
        long accessTokenExpirySeconds = accessTokenExpiryMinutes * 60L;
        long refreshTokenExpirySeconds = refreshTokenExpiryDays * 86400L;

        Instant accessExpiresAt = Instant.now().plusSeconds(accessTokenExpirySeconds);
        String accessToken = generateTeamAccessToken(team, deviceId, accessExpiresAt);
        String refreshTokenStr = generateRefreshTokenValue();

        RefreshToken refreshToken = new RefreshToken();
        refreshToken.setToken(refreshTokenStr);
        refreshToken.setSubject(team.getId().toString());
        refreshToken.setName(team.getName());
        refreshToken.setRoles(Set.of("team"));
        refreshToken.setExpiresAt(Instant.now().plusSeconds(refreshTokenExpirySeconds));
        refreshToken.setRevoked(false);
        refreshTokenRepository.save(refreshToken);

        Log.infof("Team check-in tokens issued - teamId: %s, teamName: %s, gameId: %s, deviceId: %s",
            team.getId(), team.getName(), team.getGameId(), deviceId);

        return new TokenResult(
            accessToken,
            refreshTokenStr,
            accessTokenExpirySeconds,
            team.getId().toString(),
            team.getName(),
            "team"
        );
    }

    /**
     * Build an access JWT for a team, optionally embedding a `did` claim so the device
     * can be revoked individually later. Mirrors {@link #generateToken}, but inlined to
     * keep the public API unchanged for callers that don't need the device claim.
     */
    private String generateTeamAccessToken(Team team, UUID deviceId, Instant expiresAt) {
        var builder = Jwt.issuer(issuer)
            .subject(team.getId().toString())
            .claim("name", team.getName())
            .groups(Set.of("team"))
            .expiresAt(expiresAt);
        if (deviceId != null) {
            builder = builder.claim("did", deviceId.toString());
        }
        String token = builder.sign();
        Log.infof("Token generated - type: ACCESS, subject: %s, name: %s, roles: [team], deviceId: %s, expiresAt: %s",
            team.getId(), team.getName(), deviceId, expiresAt);
        return token;
    }

    @Override
    public void revokeTokens(UUID teamId) {
        // Delete persisted refresh tokens AND record the cutoff in Valkey so any
        // access token already in a participant's browser is rejected on its next
        // request — on any API pod.
        refreshTokenRepository.deleteBySubject(teamId.toString());
        tokenRevocationStore.revokeSubject(teamId.toString(), Instant.now());
        Log.infof("Tokens revoked - subject: %s", teamId);
    }

    @Override
    public Uni<Boolean> isAccessTokenRevoked(String subject, Instant issuedAt) {
        if (subject == null || issuedAt == null) {
            return Uni.createFrom().item(false);
        }
        return tokenRevocationStore.getSubjectCutoff(subject)
            .map(cutoff -> cutoff.map(issuedAt::isBefore).orElse(false));
    }

    @Override
    public void revokeDeviceTokens(UUID teamId, UUID deviceId) {
        if (teamId == null || deviceId == null) {
            return;
        }
        tokenRevocationStore.revokeDevice(teamId.toString(), deviceId, Instant.now());
        Log.infof("Device tokens revoked - teamId: %s, deviceId: %s", teamId, deviceId);
    }

    @Override
    public Uni<Boolean> isDeviceTokenRevoked(String subject, UUID deviceId, Instant issuedAt) {
        if (subject == null || deviceId == null || issuedAt == null) {
            return Uni.createFrom().item(false);
        }
        return tokenRevocationStore.getDeviceCutoff(subject, deviceId)
            .map(cutoff -> cutoff.map(issuedAt::isBefore).orElse(false));
    }

    @Override
    public void storeRefreshToken(String refreshTokenStr, String subject, String name, Set<String> roles, long expiresAt) {
        RefreshToken refreshToken = new RefreshToken();
        refreshToken.setToken(refreshTokenStr);
        refreshToken.setSubject(subject);
        refreshToken.setName(name);
        refreshToken.setRoles(roles);
        refreshToken.setExpiresAt(Instant.ofEpochSecond(expiresAt));
        refreshToken.setRevoked(false);
        refreshTokenRepository.save(refreshToken);
    }

    private void cleanupExpiredTokens() {
        long now = Instant.now().getEpochSecond();
        registrationTokens.entrySet().removeIf(e -> e.getValue().expiresAt() < now);
        // Cleanup expired refresh tokens from database
        refreshTokenRepository.deleteExpiredTokens(Instant.now());
    }

    @Override
    public String generateRefreshTokenValue() {
        byte[] randomBytes = new byte[REFRESH_TOKEN_BYTE_LENGTH];
        SecureRandomHolder.INSTANCE.nextBytes(randomBytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(randomBytes);
    }

    @Override
    public String generateToken(String userId, String name, String role, Instant expiresAt, TokenType tokenType) {
        return generateToken(userId, name, Set.of(Role.fromValue(role)), expiresAt, tokenType);
    }

    @Override
    public String generateToken(String userId, String name, Set<Role> roles, Instant expiresAt, TokenType tokenType) {
        if (tokenType == TokenType.REFRESH) {
            throw new IllegalArgumentException("Refresh tokens must be opaque. Use generateRefreshTokenValue().");
        }
        // Convert Role enum set to lowercase string array for JWT groups claim
        Set<String> roleNames = roles.stream()
            .map(Role::toString)
            .collect(Collectors.toSet());

        String token = Jwt.issuer(issuer)
            .subject(userId)
            .claim("name", name)
            .groups(roleNames)
            .expiresAt(expiresAt)
            .sign();

        Log.infof("Token generated - type: %s, subject: %s, name: %s, roles: %s, expiresAt: %s",
            tokenType, userId, name, roleNames, expiresAt);

        return token;
    }

    @Override
    public Optional<Admin> adminLogin(String username, String password) {
        Optional<Admin> adminOpt = adminRepository.findByUsername(username)
            .filter(Admin::isActive)
            .filter(admin -> BcryptUtil.matches(password, admin.getPasswordHash()));

        if (adminOpt.isPresent()) {
            Admin admin = adminOpt.get();
            Log.infof("Admin login successful - username: %s, roles: %s",
                admin.getUsername(), admin.getRoles());
        } else {
            Log.warnf("Admin login failed - username: %s", username);
        }

        return adminOpt;
    }

    @Override
    public boolean updateAdminPassword(UUID adminId, String currentPassword, String newPassword) {
        Optional<Admin> adminOpt = adminRepository.findById(adminId);

        if (adminOpt.isEmpty()) {
            Log.warnf("Admin password update failed - admin not found: %s", adminId);
            return false;
        }

        Admin admin = adminOpt.get();

        // Verify current password
        if (!BcryptUtil.matches(currentPassword, admin.getPasswordHash())) {
            Log.warnf("Admin password update failed - incorrect current password - adminId: %s, username: %s",
                adminId, admin.getUsername());
            return false;
        }

        // Update to new password
        admin.setPasswordHash(BcryptUtil.bcryptHash(newPassword));
        adminRepository.save(admin);

        Log.infof("Admin password updated - adminId: %s, username: %s", adminId, admin.getUsername());
        return true;
    }

    @Override
    public TeamInviteLinkResponse generateTeamInviteToken(UUID teamId) {
        Team team = teamRepository.findById(teamId)
            .orElseThrow(() -> new IllegalArgumentException("Team not found: " + teamId));

        UUID gameId = team.getGameId();
        if (gameId == null) {
            throw new IllegalArgumentException("Team is not assigned to a game: " + teamId);
        }

        String code = inviteCodeStore.issue(teamId, gameId);

        // The QR encodes the uppercase form: it has no '?' or '=', so it qualifies for QR
        // alphanumeric mode and stays small enough to scan off a phone screen. Scheme and
        // host are case-insensitive (RFC 3986) and the /i/ route matches case-insensitively.
        String url = frontendBaseUrl + "/i/" + code;
        String qrUrl = url.toUpperCase(Locale.ROOT);

        return new TeamInviteLinkResponse(url, qrUrl, code, inviteCodeStore.ttlSeconds());
    }

    @Override
    public Optional<TeamInvite> validateTeamInviteToken(String token) {
        return inviteCodeStore.resolve(token);
    }

}
