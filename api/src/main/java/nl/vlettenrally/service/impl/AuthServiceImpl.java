package nl.vlettenrally.service.impl;

import io.smallrye.jwt.build.Jwt;
import io.quarkus.scheduler.Scheduled;
import io.quarkus.logging.Log;
import io.quarkus.elytron.security.common.BcryptUtil;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import nl.vlettenrally.data.domain.Admin;
import nl.vlettenrally.data.enums.Role;
import nl.vlettenrally.data.enums.TokenType;
import nl.vlettenrally.data.domain.Game;
import nl.vlettenrally.data.domain.Team;
import nl.vlettenrally.data.repository.AdminRepository;
import nl.vlettenrally.data.repository.GameRepository;
import nl.vlettenrally.data.repository.TeamRepository;
import nl.vlettenrally.service.AuthService;
import org.eclipse.microprofile.config.inject.ConfigProperty;

import java.time.Instant;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;
import nl.vlettenrally.data.dto.auth.GameCheckInLinkResponse;
import nl.vlettenrally.data.dto.auth.RegistrationTokenData;
import nl.vlettenrally.data.dto.auth.TokenResult;
import nl.vlettenrally.data.dto.auth.RegistrationResult;
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
    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    @Inject
    TeamRepository teamRepository;

    @Inject
    GameRepository gameRepository;

    @Inject
    RefreshTokenRepository refreshTokenRepository;

    @Inject
    AdminRepository adminRepository;

    @ConfigProperty(name = "mp.jwt.verify.issuer", defaultValue = "vlettenrally")
    String issuer;


    @ConfigProperty(name = "mp.jwt.verify.audience", defaultValue = "vlettenrally-audience")
    String audience;

    @ConfigProperty(name = "smallrye.jwt.sign.key-location", defaultValue = "privateKey.pem")
    String keyLocation;

    @ConfigProperty(name = "vlettenrally.jwt.access-token-expiry-minutes", defaultValue = "15")
    int accessTokenExpiryMinutes;

    @ConfigProperty(name = "vlettenrally.jwt.refresh-token-expiry-days", defaultValue = "1")
    int refreshTokenExpiryDays;

    @ConfigProperty(name = "vlettenrally.frontend.base-url", defaultValue = "http://localhost:5173")
    String frontendBaseUrl;

    // In-memory storage for registration tokens (short-lived, can remain in-memory)
    private final Map<String, RegistrationTokenData> registrationTokens = new ConcurrentHashMap<>();

    // Track when all teams in a game have checked in (for expiry calculation)
    private final Map<UUID, Instant> allTeamsCheckedInAt = new ConcurrentHashMap<>();

    @Override
    public RegistrationResult registerTeam(
            String gameId,
            String scoutingGroup,
            String emergencyContact,
            List<String> participantNames,
            String bookingId) {

        // Create new team
        Team team = new Team();
        team.setGameId(UUID.fromString(gameId));
        team.setName(scoutingGroup != null ? scoutingGroup : "Team");
        team.setDescription(scoutingGroup);
        team.setParticipants(participantNames != null ? participantNames : Collections.emptyList());
        team.setNotes(bookingId);
        team.setCheckedIn(false);
        team.setTotalPoints(0);

        Team savedTeam = teamRepository.save(team);

        // Generate registration token (valid for 15 minutes)
        String registrationToken = UUID.randomUUID().toString();
        long expiresAt = Instant.now().plusSeconds(900).getEpochSecond();

        registrationTokens.put(registrationToken,
            new RegistrationTokenData(savedTeam.getId().toString(), team.getName(), "team", expiresAt, false));

        cleanupExpiredTokens();

        Log.infof("Team registration token created - teamId: %s, teamName: %s, gameId: %s",
            savedTeam.getId(), team.getName(), gameId);

        return new RegistrationResult(registrationToken, savedTeam.getId().toString(), expiresAt);
    }

    @Override
    public Optional<TokenResult> exchangeToken(String registrationToken) {
        RegistrationTokenData tokenData = registrationTokens.get(registrationToken);

        if (tokenData == null) {
            return Optional.empty();
        }

        boolean isGameToken = "checkin".equalsIgnoreCase(tokenData.role());

        if (!isGameToken && tokenData.used()) {
            return Optional.empty();
        }

        Instant now = Instant.now();

        if (isGameToken) {
            Optional<Game> gameOpt = gameRepository.findById(UUID.fromString(tokenData.subject()));
            if (gameOpt.isEmpty()) {
                registrationTokens.remove(registrationToken);
                return Optional.empty();
            }

            Game game = gameOpt.get();
            Instant calculatedExpiry = calculateCheckInExpiry(game);
            if (now.isAfter(calculatedExpiry)) {
                registrationTokens.remove(registrationToken);
                return Optional.empty();
            }

            if (calculatedExpiry.getEpochSecond() != tokenData.expiresAt()) {
                registrationTokens.put(registrationToken, new RegistrationTokenData(
                    tokenData.subject(),
                    tokenData.name(),
                    tokenData.role(),
                    calculatedExpiry.getEpochSecond(),
                    false
                ));
            }

            // Return a result that signals team selection is required
            Log.infof("Game check-in token validated - gameId: %s, gameName: %s", tokenData.subject(), tokenData.name());

            return Optional.of(new TokenResult(
                null,
                null,
                0L,
                tokenData.subject(),
                tokenData.name(),
                tokenData.role()
            ));
        }

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
    public Optional<TokenResult> refreshToken(String refreshTokenStr) {
        Optional<RefreshToken> tokenOpt = refreshTokenRepository.findByToken(refreshTokenStr);

        if (tokenOpt.isEmpty()) {
            return Optional.empty();
        }

        RefreshToken tokenData = tokenOpt.get();

        // Check if token is already revoked (replay attack detection)
        if (tokenData.isRevoked()) {
            Log.warnf("SECURITY: Replay attack detected - subject: %s, name: %s, revoking all tokens",
                tokenData.getSubject(), tokenData.getName());
            // Possible token replay attack - revoke all tokens for this user
            try {
                UUID userId = UUID.fromString(tokenData.getSubject());
                revokeTokens(userId);
            } catch (IllegalArgumentException e) {
                // Invalid UUID, just delete this token
                refreshTokenRepository.deleteByToken(refreshTokenStr);
            }
            return Optional.empty();
        }

        if (Instant.now().isAfter(tokenData.getExpiresAt())) {
            Log.warnf("Token refresh failed - expired token - subject: %s, name: %s",
                tokenData.getSubject(), tokenData.getName());
            refreshTokenRepository.delete(tokenData.getId());
            return Optional.empty();
        }

        // Fetch current user data from database to ensure fresh roles/permissions
        // Try to fetch as Admin first, then as Team
        UUID userId;
        try {
            userId = UUID.fromString(tokenData.getSubject());
        } catch (IllegalArgumentException e) {
            Log.warnf("Token refresh failed - invalid subject format - subject: %s", tokenData.getSubject());
            return Optional.empty();
        }

        String newAccessToken;
        String newRefreshTokenStr;
        Set<String> rolesForStorage;
        long accessTokenExpirySeconds = accessTokenExpiryMinutes * 60L;
        long refreshTokenExpirySeconds = refreshTokenExpiryDays * 86400L;

        // Try to get as Admin first
        Optional<Admin> adminOpt = adminRepository.findById(userId);

        if (adminOpt.isPresent()) {
            Admin admin = adminOpt.get();

            // Use roles from database, not from stored token
            Set<Role> currentRoles = admin.getRoles();
            newAccessToken = generateToken(admin.getId().toString(), admin.getUsername(), currentRoles,
                Instant.now().plusSeconds(accessTokenExpirySeconds), TokenType.ACCESS);
            newRefreshTokenStr = generateRefreshTokenValue();

            // Store roles as lowercase strings
            rolesForStorage = currentRoles.stream()
                .map(Role::toString)
                .collect(Collectors.toSet());
        } else {
            // Try to get as Team
            Optional<Team> teamOpt = teamRepository.findById(userId);

            if (teamOpt.isEmpty()) {
                Log.warnf("Token refresh failed - user not found - userId: %s", userId);
                return Optional.empty();
            }

            Team team = teamOpt.get();

            // Use data from database
            newAccessToken = generateToken(team.getId().toString(), team.getName(), "team",
                Instant.now().plusSeconds(accessTokenExpirySeconds), TokenType.ACCESS);
            newRefreshTokenStr = generateRefreshTokenValue();

            rolesForStorage = java.util.Collections.singleton("team");
        }

        // Mark old refresh token as revoked (token rotation)
        tokenData.setRevoked(true);
        refreshTokenRepository.save(tokenData);

        // Store new refresh token with fresh data
        RefreshToken newRefreshToken = new RefreshToken();
        newRefreshToken.setToken(newRefreshTokenStr);
        newRefreshToken.setSubject(userId.toString());
        newRefreshToken.setName(tokenData.getName()); // Keep original name for logging
        newRefreshToken.setRoles(rolesForStorage);
        newRefreshToken.setExpiresAt(Instant.now().plusSeconds(refreshTokenExpirySeconds));
        newRefreshToken.setRevoked(false);
        refreshTokenRepository.save(newRefreshToken);

        Log.infof("Token refresh completed - subject: %s, name: %s, role(s): %s",
            userId, tokenData.getName(), rolesForStorage);

        return Optional.of(new TokenResult(
            newAccessToken,
            newRefreshTokenStr,
            accessTokenExpirySeconds,
            userId.toString(),
            tokenData.getName(),
            String.join(",", rolesForStorage)
        ));
    }

    @Override
    public Optional<TokenResult> selectTeam(UUID gameId, UUID teamId) {
        Optional<Game> gameOpt = gameRepository.findById(gameId);
        if (gameOpt.isEmpty()) {
            return Optional.empty();
        }

        Game game = gameOpt.get();
        Instant now = Instant.now();
        Instant calculatedExpiry = calculateCheckInExpiry(game);
        if (now.isAfter(calculatedExpiry)) {
            return Optional.empty();
        }

        Optional<Team> teamOpt = teamRepository.findById(teamId);
        if (teamOpt.isEmpty()) {
            return Optional.empty();
        }

        Team team = teamOpt.get();
        if (!game.getId().equals(team.getGameId())) {
            return Optional.empty();
        }

        // Prevent issuing tokens for teams that are already checked in
        if (team.isCheckedIn()) {
            throw new IllegalStateException("Team is already checked in");
        }

        // Generate tokens using config-driven durations
        long accessTokenExpirySeconds = accessTokenExpiryMinutes * 60L;
        long refreshTokenExpirySeconds = refreshTokenExpiryDays * 86400L;

        String accessToken = generateToken(team.getId().toString(), team.getName(), "team", Instant.now().plusSeconds(accessTokenExpirySeconds), TokenType.ACCESS);
        String refreshTokenStr = generateRefreshTokenValue();

        // Store refresh token in database for persistence
        RefreshToken refreshToken = new RefreshToken();
        refreshToken.setToken(refreshTokenStr);
        refreshToken.setSubject(team.getId().toString());
        refreshToken.setName(team.getName());
        refreshToken.setRoles(Set.of("team"));
        refreshToken.setExpiresAt(Instant.now().plusSeconds(refreshTokenExpirySeconds));
        refreshToken.setRevoked(false);
        refreshTokenRepository.save(refreshToken);

        Log.infof("Team check-in tokens issued - teamId: %s, teamName: %s, gameId: %s",
            team.getId(), team.getName(), gameId);

        return Optional.of(new TokenResult(
            accessToken,
            refreshTokenStr,
            accessTokenExpirySeconds,
            team.getId().toString(),
            team.getName(),
            "team"
        ));
    }

    @Override
    public boolean isCheckInTokenValid(UUID gameId) {
        Optional<Game> gameOpt = gameRepository.findById(gameId);
        if (gameOpt.isEmpty()) {
            return false;
        }

        Instant now = Instant.now();
        Instant calculatedExpiry = calculateCheckInExpiry(gameOpt.get());
        return !now.isAfter(calculatedExpiry);
    }

    @Override
    public void revokeTokens(UUID teamId) {
        // Delete all tokens for the team
        refreshTokenRepository.deleteBySubject(teamId.toString());
        Log.infof("Tokens revoked - subject: %s", teamId);
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

    /**
     * Periodically update and expire game-scoped registration tokens
     */
    @Scheduled(every = "5m")
    void refreshGameTokenExpiries() {
        Instant now = Instant.now();

        registrationTokens.forEach((token, data) -> {
            if (!"checkin".equalsIgnoreCase(data.role())) {
                return;
            }

            Optional<Game> gameOpt = gameRepository.findById(UUID.fromString(data.subject()));
            if (gameOpt.isEmpty()) {
                registrationTokens.remove(token);
                return;
            }

            Instant calculatedExpiry = calculateCheckInExpiry(gameOpt.get());
            if (now.isAfter(calculatedExpiry)) {
                registrationTokens.remove(token);
                return;
            }

            if (calculatedExpiry.getEpochSecond() != data.expiresAt()) {
                registrationTokens.put(token, new RegistrationTokenData(
                    data.subject(),
                    data.name(),
                    data.role(),
                    calculatedExpiry.getEpochSecond(),
                    false
                ));
            }
        });
    }

    @Override
    public String generateRefreshTokenValue() {
        byte[] randomBytes = new byte[REFRESH_TOKEN_BYTE_LENGTH];
        SECURE_RANDOM.nextBytes(randomBytes);
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
    public GameCheckInLinkResponse generateGameCheckInLink(UUID gameId) {
        // Load the game to get its expiry time
        Optional<Game> gameOpt = gameRepository.findById(gameId);
        if (gameOpt.isEmpty()) {
            throw new IllegalArgumentException("Game not found: " + gameId);
        }

        Game game = gameOpt.get();

        // Calculate expiry: min(allTeamsCheckedInTime + 5min, gameStartTime)
        long expiresAt = calculateCheckInExpiry(game).getEpochSecond();

        // Generate game-scoped registration token (valid for duration until expiry)
        String registrationToken = generateToken(gameId.toString(), game.getName(), "checkin", Instant.ofEpochSecond(expiresAt), TokenType.CHECKIN);

        // Construct the full check-in URL
        String checkInUrl = frontendBaseUrl + "/check-in?token=" + registrationToken;

        String expiryReason = "Expires 5 minutes after all teams check in or at game start time";

        Log.infof("Game check-in link generated - gameId: %s, gameName: %s, expiresAt: %s",
            gameId, game.getName(), Instant.ofEpochSecond(expiresAt));

        return new GameCheckInLinkResponse(checkInUrl, registrationToken, expiresAt, expiryReason);
    }

    /**
     * Calculate when a game's check-in token should expire.
     *
     * Expiry rules:
     * 1. If all teams have checked in, token expires 5 minutes after the last check-in
     * 2. If game start time has passed, token expires immediately
     * 3. Otherwise, token expires at game start time
     */
    private Instant calculateCheckInExpiry(Game game) {
        // Get all teams for this game
        List<Team> teams = teamRepository.findByGameId(game.getId());

        // Check if all teams are checked in
        boolean allCheckedIn = !teams.isEmpty() && teams.stream().allMatch(Team::isCheckedIn);

        Instant now = Instant.now();

        if (allCheckedIn) {
            allTeamsCheckedInAt.computeIfAbsent(game.getId(), key -> now);
        } else {
            allTeamsCheckedInAt.remove(game.getId());
        }

        Instant allCheckedInAt = allTeamsCheckedInAt.get(game.getId());
        Instant allCheckedInExpiry = allCheckedInAt != null ? allCheckedInAt.plusSeconds(300) : null;

        // Check if game has already started
        if (game.getStartTime() != null && now.isAfter(game.getStartTime())) {
            return now; // Already expired
        }

        Instant expiry = game.getStartTime() != null ? game.getStartTime() : now.plusSeconds(86400);

        if (allCheckedInExpiry != null && allCheckedInExpiry.isBefore(expiry)) {
            expiry = allCheckedInExpiry;
        }

        return expiry;
    }
}
