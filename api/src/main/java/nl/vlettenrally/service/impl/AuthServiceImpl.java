package nl.vlettenrally.service.impl;

import io.smallrye.jwt.build.Jwt;
import io.quarkus.scheduler.Scheduled;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import nl.vlettenrally.data.domain.AdminRole;
import nl.vlettenrally.data.domain.Game;
import nl.vlettenrally.data.domain.Team;
import nl.vlettenrally.data.repository.GameRepository;
import nl.vlettenrally.data.repository.TeamRepository;
import nl.vlettenrally.service.AuthService;
import org.eclipse.microprofile.config.inject.ConfigProperty;

import java.time.Instant;
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

    @Inject
    TeamRepository teamRepository;

    @Inject
    GameRepository gameRepository;

    @Inject
    RefreshTokenRepository refreshTokenRepository;

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
            String boatNumber,
            String scoutingGroup,
            String emergencyContact,
            List<String> participantNames,
            String bookingId) {

        // Create new team
        Team team = new Team();
        team.setGameId(UUID.fromString(gameId));
        team.setBoatNumber(boatNumber);
        team.setName(scoutingGroup != null ? scoutingGroup : "Team " + boatNumber);
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
        
        String accessToken = generateToken(tokenData.subject(), tokenData.name(), tokenData.role().toLowerCase(), Instant.now().plusSeconds(accessTokenExpirySeconds));
        String refreshTokenStr = generateToken(tokenData.subject(), tokenData.name(), tokenData.role(), Instant.now().plusSeconds(refreshTokenExpirySeconds));

        // Store refresh token in database for persistence
        RefreshToken refreshToken = new RefreshToken();
        refreshToken.setToken(refreshTokenStr);
        refreshToken.setSubject(tokenData.subject());
        refreshToken.setName(tokenData.name());
        refreshToken.setRole(tokenData.role());
        refreshToken.setExpiresAt(Instant.now().plusSeconds(refreshTokenExpirySeconds));
        refreshToken.setRevoked(false);
        refreshTokenRepository.save(refreshToken);

        // Remove used registration token
        registrationTokens.remove(registrationToken);

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
            refreshTokenRepository.delete(tokenData.getId());
            return Optional.empty();
        }

        // Generate new tokens using config-driven durations
        long accessTokenExpirySeconds = accessTokenExpiryMinutes * 60L;
        long refreshTokenExpirySeconds = refreshTokenExpiryDays * 86400L;
        
        String newAccessToken = generateToken(tokenData.getSubject(), tokenData.getName(), tokenData.getRole().toLowerCase(), Instant.now().plusSeconds(accessTokenExpirySeconds));
        String newRefreshTokenStr = generateToken(tokenData.getSubject(), tokenData.getName(), tokenData.getRole(), Instant.now().plusSeconds(refreshTokenExpirySeconds));

        // Mark old refresh token as revoked (token rotation)
        tokenData.setRevoked(true);
        refreshTokenRepository.save(tokenData);

        // Store new refresh token
        RefreshToken newRefreshToken = new RefreshToken();
        newRefreshToken.setToken(newRefreshTokenStr);
        newRefreshToken.setSubject(tokenData.getSubject());
        newRefreshToken.setName(tokenData.getName());
        newRefreshToken.setRole(tokenData.getRole());
        newRefreshToken.setExpiresAt(Instant.now().plusSeconds(refreshTokenExpirySeconds));
        newRefreshToken.setRevoked(false);
        refreshTokenRepository.save(newRefreshToken);

        return Optional.of(new TokenResult(
            newAccessToken,
            newRefreshTokenStr,
            accessTokenExpirySeconds,
            tokenData.getSubject(),
            tokenData.getName(),
            tokenData.getRole()
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

        String accessToken = generateToken(team.getId().toString(), team.getName(), "team", Instant.now().plusSeconds(accessTokenExpirySeconds));
        String refreshTokenStr = generateToken(team.getId().toString(), team.getName(), "team", Instant.now().plusSeconds(refreshTokenExpirySeconds));

        // Store refresh token in database for persistence
        RefreshToken refreshToken = new RefreshToken();
        refreshToken.setToken(refreshTokenStr);
        refreshToken.setSubject(team.getId().toString());
        refreshToken.setName(team.getName());
        refreshToken.setRole("team");
        refreshToken.setExpiresAt(Instant.now().plusSeconds(refreshTokenExpirySeconds));
        refreshToken.setRevoked(false);
        refreshTokenRepository.save(refreshToken);

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
    }

    @Override
    public void storeRefreshToken(String refreshTokenStr, String subject, String name, String role, long expiresAt) {
        RefreshToken refreshToken = new RefreshToken();
        refreshToken.setToken(refreshTokenStr);
        refreshToken.setSubject(subject);
        refreshToken.setName(name);
        refreshToken.setRole(role);
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
    public String generateToken(String userId, String name, String role, Instant expiresAt) {
        String token = Jwt.issuer(issuer)
            .subject(userId)
            .claim("name", name)
            .groups(role)
            .expiresAt(expiresAt)
            .sign();

        return token;
    }

    @Override
    public String generateAdminToken(String userId, String name, Set<AdminRole> roles, Instant expiresAt) {
        // Convert AdminRole enum set to lowercase string array for JWT groups claim
        Set<String> roleNames = roles.stream()
            .map(role -> role.name().toLowerCase())
            .collect(Collectors.toSet());

        String token = Jwt.issuer(issuer)
            .subject(userId)
            .claim("name", name)
            .groups(roleNames)
            .expiresAt(expiresAt)
            .sign();

        return token;
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
        String registrationToken = generateToken(gameId.toString(), game.getName(), "checkin", Instant.ofEpochSecond(expiresAt));

        // Construct the full check-in URL
        String checkInUrl = frontendBaseUrl + "/check-in?token=" + registrationToken;

        String expiryReason = "Expires 5 minutes after all teams check in or at game start time";

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
