package nl.vlettenrally.service.impl;

import io.smallrye.jwt.build.Jwt;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
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
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import nl.vlettenrally.data.dto.auth.RegistrationTokenData;
import nl.vlettenrally.data.dto.auth.TokenResult;
import nl.vlettenrally.data.dto.auth.RefreshTokenData;
import nl.vlettenrally.data.dto.auth.RegistrationResult;

/**
 * Implementation of AuthService
 * Uses in-memory storage for tokens (use Redis or database in production)
 */
@ApplicationScoped
public class AuthServiceImpl implements AuthService {

    @Inject
    TeamRepository teamRepository;

    @Inject
    GameRepository gameRepository;

    @ConfigProperty(name = "mp.jwt.verify.issuer", defaultValue = "vlettenrally")
    String issuer;


    @ConfigProperty(name = "mp.jwt.verify.audience", defaultValue = "vlettenrally-audience")
    String audience;

    @ConfigProperty(name = "smallrye.jwt.sign.key-location", defaultValue = "privateKey.pem")
    String keyLocation;

    // In-memory storage for registration tokens
    private final Map<String, RegistrationTokenData> registrationTokens = new ConcurrentHashMap<>();
    
    // In-memory storage for refresh tokens
    private final Map<String, RefreshTokenData> refreshTokens = new ConcurrentHashMap<>();

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

        if (tokenData.used()) {
            return Optional.empty();
        }

        if (Instant.now().getEpochSecond() > tokenData.expiresAt()) {
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

        // Generate tokens
        String accessToken = generateMockToken(tokenData.subject(), tokenData.name(), tokenData.role(), Instant.now().plusSeconds(3600));
        String refreshToken = generateMockToken(tokenData.subject(), tokenData.name(), tokenData.role(), Instant.now().plusSeconds(86400));

        // Remove used registration token
        registrationTokens.remove(registrationToken);

        return Optional.of(new TokenResult(
            accessToken,
            refreshToken,
            3600, // 1 hour
            tokenData.subject(),
            tokenData.name(),
            tokenData.role()
   
        ));
    }

    @Override
    public Optional<TokenResult> refreshToken(String refreshToken) {
        RefreshTokenData tokenData = refreshTokens.get(refreshToken);

        if (tokenData == null) {
            return Optional.empty();
        }

        if (Instant.now().getEpochSecond() > tokenData.expiresAt()) {
            refreshTokens.remove(refreshToken);
            return Optional.empty();
        }

        // Generate new access token
        String newAccessToken = generateMockToken(tokenData.subject(), tokenData.name(), tokenData.role(), Instant.now().plusSeconds(3600));

        return Optional.of(new TokenResult(
            newAccessToken,
            refreshToken,
            3600,
            tokenData.subject(),
            tokenData.name(),
            tokenData.role()
        ));
    }

    @Override
    public void revokeTokens(UUID teamId) {
        // Remove all tokens for the team
        refreshTokens.entrySet().removeIf(e -> e.getValue().subject().equals(teamId.toString()));
    }

    private void cleanupExpiredTokens() {
        long now = Instant.now().getEpochSecond();
        registrationTokens.entrySet().removeIf(e -> e.getValue().expiresAt() < now);
        refreshTokens.entrySet().removeIf(e -> e.getValue().expiresAt() < now);
    }

    @Override
    public String generateMockToken(String userId, String name, String role, Instant expiresAt) {
        String token = Jwt.issuer(issuer)
            .subject(userId)
            .claim("name", name)
            .groups(role)
            .expiresAt(expiresAt)
            .sign();

        return token;
    }
}
