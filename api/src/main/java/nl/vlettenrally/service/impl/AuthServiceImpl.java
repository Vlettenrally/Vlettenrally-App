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
import nl.vlettenrally.data.domain.Team;
import nl.vlettenrally.data.repository.AdminRepository;
import nl.vlettenrally.data.repository.GameRepository;
import nl.vlettenrally.data.repository.TeamRepository;
import nl.vlettenrally.service.AuthService;
import nl.vlettenrally.service.InviteCodeStore;
import nl.vlettenrally.service.TokenRevocationStore;
import org.eclipse.microprofile.config.inject.ConfigProperty;

import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.HashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;
import nl.vlettenrally.data.dto.auth.RefreshOutcome;
import nl.vlettenrally.data.dto.auth.RegistrationTokenData;
import nl.vlettenrally.data.dto.auth.TeamInvite;
import nl.vlettenrally.data.dto.auth.TeamInviteLinkResponse;
import nl.vlettenrally.data.dto.auth.TokenResult;
import nl.vlettenrally.observability.AuthMetrics;
import nl.vlettenrally.observability.AuthMetrics.ChainLookup;
import nl.vlettenrally.observability.AuthMetrics.ChainOutcome;
import nl.vlettenrally.observability.AuthMetrics.Principal;
import nl.vlettenrally.observability.AuthMetrics.RefreshResult;
import nl.vlettenrally.observability.AuthMetrics.RevokeScope;
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

    @Inject
    AuthMetrics authMetrics;

    @ConfigProperty(name = "mp.jwt.verify.issuer", defaultValue = "vlettenrally")
    String issuer;


    @ConfigProperty(name = "mp.jwt.verify.audience", defaultValue = "vlettenrally-audience")
    String audience;

    @ConfigProperty(name = "vlettenrally.jwt.access-token-expiry-minutes", defaultValue = "60")
    int accessTokenExpiryMinutes;

    @ConfigProperty(name = "vlettenrally.jwt.refresh-token-expiry-days", defaultValue = "7")
    int refreshTokenExpiryDays;

    @ConfigProperty(name = "vlettenrally.jwt.refresh-token-reuse-grace-seconds", defaultValue = "30")
    int refreshTokenReuseGraceSeconds;

    // How long after rotation a replay is still considered routine. This is a *classification*
    // threshold, not a gate: a replay that resolves to a live successor is always served, however
    // old it is, because a backgrounded phone that lost its rotation response is not an attacker.
    // Beyond the window the replay is logged at WARN and counted with stale=true so that genuine
    // abuse stays visible. Stolen-token exposure is capped by refresh-token-expiry-days instead.
    @ConfigProperty(name = "vlettenrally.jwt.refresh-token-replay-window-seconds", defaultValue = "86400")
    int refreshTokenReplayWindowSeconds;

    // Bounds the legacy replaced_by_token walk, used only for rows written before family_id
    // existed (or by an older pod mid-rolling-deploy). Rows with a family id resolve in one query.
    @ConfigProperty(name = "vlettenrally.jwt.refresh-token-replay-max-hops", defaultValue = "10")
    int refreshTokenReplayMaxHops;

    // How often one superseded token may be resolved back to its lineage's live head before the
    // repetition itself is treated as reuse. Losing a rotation response twice on a bad network is
    // plausible; presenting the same dead token four times means nothing is storing what we return.
    @ConfigProperty(name = "vlettenrally.jwt.refresh-token-max-replays-per-token", defaultValue = "3")
    int refreshTokenMaxReplaysPerToken;

    // Escape hatch: set false to keep detecting and counting replays without ever revoking, e.g.
    // for a rally day, without a redeploy.
    @ConfigProperty(name = "vlettenrally.jwt.refresh-token-replay-hard-revoke-enabled", defaultValue = "true")
    boolean refreshTokenReplayHardRevokeEnabled;

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
        RefreshToken refreshToken = newRefreshTokenRow(
            refreshTokenStr,
            tokenData.subject(),
            tokenData.name(),
            java.util.Collections.singleton(tokenData.role()),
            Instant.now().plusSeconds(refreshTokenExpirySeconds),
            null,
            null);
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
    public RefreshOutcome refreshToken(String refreshTokenStr) {
        Optional<RefreshToken> tokenOpt = refreshTokenRepository.findByToken(refreshTokenStr);

        if (tokenOpt.isEmpty()) {
            return rejected("unknown refresh token", null);
        }

        RefreshToken tokenData = tokenOpt.get();

        if (Instant.now().isAfter(tokenData.getExpiresAt())) {
            Log.warnf("Token refresh failed - expired token - subject: %s, name: %s",
                tokenData.getSubject(), tokenData.getName());
            refreshTokenRepository.delete(tokenData.getId());
            return rejected("refresh token expired", null);
        }

        // Resolve the subject's current identity (fresh roles/name from the DB). Needed both
        // for normal rotation and for minting a fresh access token on idempotent replay.
        UUID userId;
        try {
            userId = UUID.fromString(tokenData.getSubject());
        } catch (IllegalArgumentException e) {
            Log.warnf("Token refresh failed - invalid subject format - subject: %s", tokenData.getSubject());
            return rejected("invalid subject", null);
        }

        ResolvedIdentity identity = resolveIdentity(userId);
        if (identity == null) {
            Log.warnf("Token refresh failed - user not found - userId: %s", userId);
            return rejected("subject no longer exists", null);
        }

        long accessTokenExpirySeconds = accessTokenExpiryMinutes * 60L;
        long refreshTokenExpirySeconds = refreshTokenExpiryDays * 86400L;

        // Token was already rotated before we even looked -> concurrent refresh or a replayed
        // (possibly lost-response) token. Never nuke on the happy path; try to resolve the chain.
        if (tokenData.isRevoked()) {
            return handleReplay(tokenData, identity, accessTokenExpirySeconds);
        }

        // Atomically claim the rotation. Exactly one concurrent caller flips revoked=false->true
        // and records the successor token; everyone else gets 0 rows and replays instead.
        String newRefreshTokenStr = generateRefreshTokenValue();
        int rowsWon = refreshTokenRepository.markRevokedIfActive(
            tokenData.getId(), Instant.now(), newRefreshTokenStr);

        if (rowsWon == 0) {
            // A concurrent request won the rotation between our read and update. The re-read MUST
            // bypass the persistence context: markRevokedIfActive is a bulk UPDATE and the winner
            // committed in another transaction, so a plain findById would return the instance we
            // loaded above — revoked=false, no successor — and the chain would look like an attack.
            Optional<RefreshToken> reread = refreshTokenRepository.findByIdFresh(tokenData.getId());
            if (reread.isEmpty()) {
                // Row deleted underneath us mid-flight. Nothing to resolve; let the client retry.
                return retryable("token row disappeared during rotation", identity);
            }
            return handleReplay(reread.get(), identity, accessTokenExpirySeconds);
        }

        // We won the rotation: mint the new access token and persist the successor refresh token.
        String newAccessToken = mintAccessToken(identity, accessTokenExpirySeconds, tokenData.getDeviceId());

        RefreshToken newRefreshToken = newRefreshTokenRow(
            newRefreshTokenStr,
            userId.toString(),
            tokenData.getName(), // Keep original name for logging
            identity.rolesForStorage,
            Instant.now().plusSeconds(refreshTokenExpirySeconds),
            tokenData.getFamilyId(),
            tokenData.getDeviceId());
        refreshTokenRepository.save(newRefreshToken);

        Log.infof("Token refresh completed - subject: %s, name: %s, role(s): %s, family: %s, device: %s",
            userId, tokenData.getName(), identity.rolesForStorage,
            newRefreshToken.getFamilyId(), newRefreshToken.getDeviceId());

        authMetrics.refreshed(RefreshResult.ROTATED, identity.principal());
        return new RefreshOutcome.Success(new TokenResult(
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
     * <p>If the token's rotation lineage still has a usable head, return that head plus a freshly
     * minted access token, however long ago the rotation happened. This makes refresh idempotent:
     * a client that lost the rotation response — a backgrounded PWA, a phone with no signal on the
     * water — converges on the current live token instead of being locked out. Age is recorded,
     * not enforced; what caps a stolen token's usefulness is its expiry, not this method.
     *
     * <p>What does trip the tripwire is repetition: a superseded token resolved back to its head
     * more than {@code refresh-token-max-replays-per-token} times is not being stored by whoever
     * holds it. Revocation then covers that one lineage, never the team's other devices.
     */
    private RefreshOutcome handleReplay(RefreshToken presented, ResolvedIdentity identity,
                                        long accessTokenExpirySeconds) {
        ChainResolution chain = resolveLiveHead(presented);
        Instant revokedAt = presented.getRevokedAt();
        long ageSeconds = revokedAt == null ? -1 : Duration.between(revokedAt, Instant.now()).toSeconds();
        boolean stale = revokedAt != null
            && Instant.now().isAfter(revokedAt.plusSeconds(refreshTokenReplayWindowSeconds));

        authMetrics.replayed(chain.outcome(), chain.lookup(), chain.hops(), stale);

        switch (chain.outcome()) {
            case LIVE_HEAD -> {
                RefreshToken head = chain.head();
                // A client that lost a rotation response replays once and then stores what it gets
                // back. One that keeps presenting the same superseded token is storing nothing —
                // that repetition, not elapsed time, is the reuse signal worth acting on.
                int replays = refreshTokenRepository.incrementReplayCount(presented.getId());
                if (replays > refreshTokenMaxReplaysPerToken) {
                    return revokeFamily(presented, identity, ageSeconds, "repeat_replay", replays);
                }
                String message = "Idempotent refresh replay - returning live head - subject: %s, name: %s, "
                    + "family: %s, device: %s, hops: %s, ageSeconds: %s, lookup: %s";
                if (stale || chain.hops() > 1) {
                    // Worth a WARN: either the client was more than one rotation behind, or it came
                    // back long after the rotation. Both are served, both are things to watch.
                    Log.warnf(message, presented.getSubject(), presented.getName(), presented.getFamilyId(),
                        head.getDeviceId(), chain.hops(), ageSeconds, chain.lookup());
                } else {
                    Log.infof(message, presented.getSubject(), presented.getName(), presented.getFamilyId(),
                        head.getDeviceId(), chain.hops(), ageSeconds, chain.lookup());
                }
                authMetrics.refreshed(RefreshResult.REPLAY_HEAD, identity.principal());
                return new RefreshOutcome.Success(new TokenResult(
                    mintAccessToken(identity, accessTokenExpirySeconds, head.getDeviceId()),
                    head.getToken(),
                    accessTokenExpirySeconds,
                    presented.getSubject(),
                    presented.getName(),
                    String.join(",", identity.rolesForStorage)
                ));
            }
            case MISSING_LINK, HOP_LIMIT -> {
                // The lineage exists but cannot be resolved right now (a successor row was cleaned
                // up, or the walk gave up). Not evidence of anything; let the client try again.
                Log.warnf("Refresh replay unresolved - subject: %s, name: %s, family: %s, resolution: %s, "
                        + "hops: %s, ageSeconds: %s",
                    presented.getSubject(), presented.getName(), presented.getFamilyId(),
                    chain.outcome(), chain.hops(), ageSeconds);
                return retryable("refresh chain temporarily unresolvable", identity);
            }
            case EXPIRED_END -> {
                Log.infof("Refresh replay on an expired chain - subject: %s, name: %s, family: %s",
                    presented.getSubject(), presented.getName(), presented.getFamilyId());
                return rejected("refresh chain expired", identity);
            }
            case DEAD_END -> {
                if (revokedAt != null
                        && Instant.now().isBefore(revokedAt.plusSeconds(refreshTokenReuseGraceSeconds))) {
                    // Revoked moments ago with no successor recorded yet: a duplicate in-flight
                    // request that overtook the winner's write. Fail softly, revoke nothing.
                    Log.warnf("Duplicate refresh token usage within grace period - subject: %s, name: %s, "
                            + "revokedAt: %s",
                        presented.getSubject(), presented.getName(), revokedAt);
                    return retryable("duplicate refresh in flight", identity);
                }
                // Revoked, unexpired, and recording no successor at all. Rotation always records
                // one, so this is only reachable for rows revoked by a version that predates
                // replaced_by_token — or for a token value that was never ours.
                return revokeFamily(presented, identity, ageSeconds, "dead_end", presented.getReplayCount());
            }
        }
        // Unreachable: the switch above is exhaustive over ChainOutcome.
        return retryable("unclassified refresh replay", identity);
    }

    /**
     * Follow a revoked token to the still-usable head of its rotation lineage.
     *
     * <p>Rows carrying a family id resolve in a single indexed query, with no limit on how far
     * behind the caller is. The {@code replaced_by_token} walk is the fallback for rows written
     * before family ids existed, or by an older pod during a rolling deploy.
     */
    private ChainResolution resolveLiveHead(RefreshToken presented) {
        UUID familyId = presented.getFamilyId();
        if (familyId != null) {
            Optional<RefreshToken> head = refreshTokenRepository.findActiveByFamily(familyId);
            if (head.isPresent()) {
                return new ChainResolution(head.get(), -1, ChainOutcome.LIVE_HEAD, ChainLookup.FAMILY);
            }
            // Family known but nothing live in it. Fall through to the walk, which can still reach
            // a successor whose own family id was never written, and which distinguishes "ends
            // here" from "successor row is gone" — a distinction that decides whether we revoke.
        }

        RefreshToken current = presented;
        Set<UUID> seen = new HashSet<>();
        seen.add(presented.getId());

        for (int hops = 1; hops <= refreshTokenReplayMaxHops; hops++) {
            String successorToken = current.getReplacedByToken();
            if (successorToken == null) {
                return new ChainResolution(null, hops - 1, ChainOutcome.DEAD_END, ChainLookup.CHAIN_WALK);
            }
            Optional<RefreshToken> successorOpt = refreshTokenRepository.findByToken(successorToken);
            if (successorOpt.isEmpty()) {
                return new ChainResolution(null, hops, ChainOutcome.MISSING_LINK, ChainLookup.CHAIN_WALK);
            }
            RefreshToken successor = successorOpt.get();
            if (!seen.add(successor.getId())) {
                // Cycle. Should be impossible, but a corrupt chain must not become an accusation.
                return new ChainResolution(null, hops, ChainOutcome.HOP_LIMIT, ChainLookup.CHAIN_WALK);
            }
            if (Instant.now().isAfter(successor.getExpiresAt())) {
                return new ChainResolution(null, hops, ChainOutcome.EXPIRED_END, ChainLookup.CHAIN_WALK);
            }
            if (!successor.isRevoked()) {
                return new ChainResolution(successor, hops, ChainOutcome.LIVE_HEAD, ChainLookup.CHAIN_WALK);
            }
            current = successor;
        }
        return new ChainResolution(null, refreshTokenReplayMaxHops, ChainOutcome.HOP_LIMIT, ChainLookup.CHAIN_WALK);
    }

    /**
     * The replay tripwire. Scoped to the presented token's lineage: a team's devices all share the
     * same subject, so a subject-wide revocation here would log out every phone in the team, and
     * their only way back in is a physical check-in at the rally desk.
     *
     * <p>The exception is an admin, whose refresh rows carry no device id and who can simply log in
     * again with a password — there, revoking the subject is cheap and strictly safer.
     */
    private RefreshOutcome revokeFamily(RefreshToken presented, ResolvedIdentity identity, long ageSeconds,
                                        String trigger, int replays) {
        if (!refreshTokenReplayHardRevokeEnabled) {
            Log.warnf("SECURITY: Refresh token reuse detected but hard revoke is disabled - subject: %s, "
                    + "name: %s, family: %s, device: %s, trigger: %s, replays: %s, ageSeconds: %s",
                presented.getSubject(), presented.getName(), presented.getFamilyId(),
                presented.getDeviceId(), trigger, replays, ageSeconds);
            return rejected("refresh token reuse", identity);
        }

        UUID familyId = presented.getFamilyId();
        UUID deviceId = presented.getDeviceId();
        RevokeScope scope;

        if (familyId != null) {
            refreshTokenRepository.deleteByFamily(familyId);
        } else {
            refreshTokenRepository.deleteByToken(presented.getToken());
        }

        if (deviceId != null) {
            tokenRevocationStore.revokeDevice(presented.getSubject(), deviceId, revocationCutoff());
            scope = RevokeScope.FAMILY;
        } else if (identity.admin) {
            tokenRevocationStore.revokeSubject(presented.getSubject(), revocationCutoff());
            scope = RevokeScope.SUBJECT;
        } else {
            // A team row from before device ids were recorded. Deleting the lineage is as far as we
            // go: a subject cutoff would end every device's session, and the access token minted
            // from this lineage dies on its own within accessTokenExpiryMinutes anyway.
            scope = RevokeScope.FAMILY;
        }

        Log.warnf("SECURITY: Refresh token reuse - revoking lineage - subject: %s, name: %s, family: %s, "
                + "device: %s, trigger: %s, replays: %s, ageSeconds: %s, scope: %s",
            presented.getSubject(), presented.getName(), familyId, deviceId, trigger, replays,
            ageSeconds, scope);
        authMetrics.revoked(scope, identity.principal());
        return rejected("refresh token reuse", identity);
    }

    /** Where a presented-but-revoked token's lineage led. {@code head} is set only for LIVE_HEAD. */
    private record ChainResolution(RefreshToken head, int hops, ChainOutcome outcome, ChainLookup lookup) {}

    private RefreshOutcome rejected(String reason, ResolvedIdentity identity) {
        authMetrics.refreshed(RefreshResult.REJECTED, identity == null ? Principal.TEAM : identity.principal());
        return new RefreshOutcome.Rejected(reason);
    }

    private RefreshOutcome retryable(String reason, ResolvedIdentity identity) {
        authMetrics.refreshed(RefreshResult.RETRYABLE, identity == null ? Principal.TEAM : identity.principal());
        return new RefreshOutcome.Retryable(reason);
    }

    /**
     * Build a refresh-token row, inheriting the lineage of the token it replaces. Going through
     * one factory is what keeps family and device from being silently dropped on a rotation —
     * which would put the row outside family-scoped revocation.
     */
    private RefreshToken newRefreshTokenRow(String token, String subject, String name, Set<String> roles,
                                            Instant expiresAt, UUID familyId, UUID deviceId) {
        RefreshToken row = new RefreshToken();
        row.setToken(token);
        row.setSubject(subject);
        row.setName(name);
        row.setRoles(roles);
        row.setExpiresAt(expiresAt);
        row.setRevoked(false);
        // A null familyId means "start a new lineage"; the constructor already pointed it at self.
        if (familyId != null) {
            row.setFamilyId(familyId);
        }
        row.setDeviceId(deviceId);
        return row;
    }

    /**
     * The current identity of a refresh token's subject, resolved fresh from the DB so rotated
     * access tokens always carry up-to-date roles. Returns null if the subject no longer exists
     * or is a deactivated admin.
     */
    private ResolvedIdentity resolveIdentity(UUID userId) {
        Optional<Admin> adminOpt = adminRepository.findById(userId);
        if (adminOpt.isPresent()) {
            Admin admin = adminOpt.get();
            // A deactivated admin must not be able to rotate its way into a fresh access token.
            // Without this, disabling an account only stopped new logins, not existing sessions.
            if (!admin.isActive()) {
                Log.warnf("Token refresh refused - admin is deactivated - adminId: %s", userId);
                return null;
            }
            Set<Role> roles = admin.getRoles();
            Set<String> rolesForStorage = roles.stream()
                .map(Role::toString)
                .collect(Collectors.toSet());
            return new ResolvedIdentity(admin.getId().toString(), admin.getUsername(), roles, rolesForStorage, true);
        }

        Optional<Team> teamOpt = teamRepository.findById(userId);
        if (teamOpt.isPresent()) {
            Team team = teamOpt.get();
            return new ResolvedIdentity(team.getId().toString(), team.getName(),
                Set.of(Role.fromValue("team")), java.util.Collections.singleton("team"), false);
        }

        return null;
    }

    /**
     * Mint a rotated access token. The device id comes from the refresh-token row, never from the
     * request: without it the rotated token loses its `did` claim and escapes per-device
     * revocation, so "remove this device" would stop working after the first refresh.
     */
    private String mintAccessToken(ResolvedIdentity identity, long accessTokenExpirySeconds, UUID deviceId) {
        return buildAccessToken(identity.subjectId, identity.displayName, identity.roles,
            Instant.now().plusSeconds(accessTokenExpirySeconds), deviceId);
    }

    /** Immutable view of a refresh-token subject used to mint rotated access tokens. */
    private static final class ResolvedIdentity {
        final String subjectId;
        final String displayName;
        final Set<Role> roles;
        final Set<String> rolesForStorage;
        /** Admins and teams differ in how much a revocation costs them — see revokeFamily. */
        final boolean admin;

        ResolvedIdentity(String subjectId, String displayName, Set<Role> roles, Set<String> rolesForStorage,
                         boolean admin) {
            this.subjectId = subjectId;
            this.displayName = displayName;
            this.roles = roles;
            this.rolesForStorage = rolesForStorage;
            this.admin = admin;
        }

        Principal principal() {
            return admin ? Principal.ADMIN : Principal.TEAM;
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

        // A fresh login starts its own lineage (familyId null -> self), bound to this device.
        RefreshToken refreshToken = newRefreshTokenRow(
            refreshTokenStr,
            team.getId().toString(),
            team.getName(),
            Set.of("team"),
            Instant.now().plusSeconds(refreshTokenExpirySeconds),
            null,
            deviceId);
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
     * Build an access JWT for a team, embedding a `did` claim so the device can be revoked
     * individually later.
     */
    private String generateTeamAccessToken(Team team, UUID deviceId, Instant expiresAt) {
        return buildAccessToken(team.getId().toString(), team.getName(),
            Set.of(Role.fromValue("team")), expiresAt, deviceId);
    }

    /**
     * The single place an access JWT is built. Everything that mints one — first login, device
     * check-in, rotation, replay — goes through here, so the `did` claim cannot be present on
     * issue and quietly absent after the first refresh.
     */
    private String buildAccessToken(String subject, String name, Set<Role> roles, Instant expiresAt,
                                    UUID deviceId) {
        Set<String> roleNames = roles.stream()
            .map(Role::toString)
            .collect(Collectors.toSet());

        var builder = Jwt.issuer(issuer)
            .subject(subject)
            .claim("name", name)
            .groups(roleNames)
            .expiresAt(expiresAt);
        if (deviceId != null) {
            builder = builder.claim("did", deviceId.toString());
        }
        String token = builder.sign();

        Log.infof("Token generated - type: ACCESS, subject: %s, name: %s, roles: %s, deviceId: %s, expiresAt: %s",
            subject, name, roleNames, deviceId, expiresAt);

        return token;
    }

    @Override
    public void revokeTokens(UUID subjectId) {
        // Delete persisted refresh tokens AND record the cutoff in Valkey so any
        // access token already in a participant's or admin's browser is rejected on
        // its next request — on any API pod.
        refreshTokenRepository.deleteBySubject(subjectId.toString());
        tokenRevocationStore.revokeSubject(subjectId.toString(), revocationCutoff());
        Log.infof("Tokens revoked - subject: %s", subjectId);
    }

    /**
     * The cutoff to compare a token's `iat` against, rounded up to the start of the next second.
     * <p>
     * JWT `iat` only has whole-second resolution, so a token minted at second N carries
     * {@code iat = N}. Using a bare {@code Instant.now()} inside that same second produced a
     * cutoff of N too, and the {@code issuedAt.isBefore(cutoff)} test then returned false —
     * the token survived revocation entirely, for its full remaining lifetime. Rounding up
     * covers every token issued during the current second while still leaving one issued in
     * the next second (a fresh login after the revocation) valid.
     */
    private static Instant revocationCutoff() {
        return Instant.now().truncatedTo(ChronoUnit.SECONDS).plusSeconds(1);
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
        // Delete the device's refresh rows as well as writing the access-token cutoff. Without the
        // delete, a removed device simply refreshed its way back in as soon as the cutoff bit.
        refreshTokenRepository.deleteBySubjectAndDevice(teamId.toString(), deviceId);
        tokenRevocationStore.revokeDevice(teamId.toString(), deviceId, revocationCutoff());
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
        // Admin login: a new lineage with no device binding.
        RefreshToken refreshToken = newRefreshTokenRow(
            refreshTokenStr, subject, name, roles, Instant.ofEpochSecond(expiresAt), null, null);
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
        return buildAccessToken(userId, name, roles, expiresAt, null);
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
