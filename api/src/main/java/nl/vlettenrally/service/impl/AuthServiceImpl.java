package nl.vlettenrally.service.impl;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import nl.vlettenrally.domain.Team;
import nl.vlettenrally.repository.TeamRepository;
import nl.vlettenrally.repository.GameRepository;
import nl.vlettenrally.service.AuthService;

import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

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

    // In-memory storage for registration tokens
    private final Map<String, RegistrationTokenData> registrationTokens = new ConcurrentHashMap<>();
    
    // In-memory storage for refresh tokens
    private final Map<String, RefreshTokenData> refreshTokens = new ConcurrentHashMap<>();

    // In-memory storage for access tokens (for validation)
    private final Map<String, AccessTokenData> accessTokens = new ConcurrentHashMap<>();

    private static class RegistrationTokenData {
        final String teamId;
        final String gameId;
        final long expiresAt;
        boolean used;

        RegistrationTokenData(String teamId, String gameId, long expiresAt) {
            this.teamId = teamId;
            this.gameId = gameId;
            this.expiresAt = expiresAt;
            this.used = false;
        }
    }

    private static class RefreshTokenData {
        final UUID teamId;
        final UUID gameId;
        final long expiresAt;

        RefreshTokenData(UUID teamId, UUID gameId, long expiresAt) {
            this.teamId = teamId;
            this.gameId = gameId;
            this.expiresAt = expiresAt;
        }
    }

    @SuppressWarnings("unused") // gameId stored for future audit/logging purposes
    private static class AccessTokenData {
        final UUID teamId;
        final UUID gameId;
        final long expiresAt;

        AccessTokenData(UUID teamId, UUID gameId, long expiresAt) {
            this.teamId = teamId;
            this.gameId = gameId;
            this.expiresAt = expiresAt;
        }
    }

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
        team.setPoints(0);

        Team savedTeam = teamRepository.save(team);

        // Generate registration token (valid for 15 minutes)
        String registrationToken = UUID.randomUUID().toString();
        long expiresAt = Instant.now().plusSeconds(900).getEpochSecond();

        registrationTokens.put(registrationToken, 
            new RegistrationTokenData(savedTeam.getId().toString(), gameId, expiresAt));

        cleanupExpiredTokens();

        return new RegistrationResult(registrationToken, savedTeam.getId().toString(), expiresAt);
    }

    @Override
    public Optional<TokenResult> exchangeToken(String registrationToken) {
        RegistrationTokenData tokenData = registrationTokens.get(registrationToken);

        if (tokenData == null) {
            return Optional.empty();
        }

        if (tokenData.used) {
            return Optional.empty();
        }

        if (Instant.now().getEpochSecond() > tokenData.expiresAt) {
            registrationTokens.remove(registrationToken);
            return Optional.empty();
        }

        // Mark as used
        tokenData.used = true;

        UUID teamId = UUID.fromString(tokenData.teamId);
        UUID gameId = UUID.fromString(tokenData.gameId);

        // Generate tokens
        String accessToken = generateAccessToken(teamId, gameId);
        String refreshToken = generateRefreshToken(teamId, gameId);

        // Remove used registration token
        registrationTokens.remove(registrationToken);

        return Optional.of(new TokenResult(
            accessToken,
            refreshToken,
            3600, // 1 hour
            tokenData.teamId,
            tokenData.gameId
        ));
    }

    @Override
    public Optional<TokenResult> refreshToken(String refreshToken) {
        RefreshTokenData tokenData = refreshTokens.get(refreshToken);

        if (tokenData == null) {
            return Optional.empty();
        }

        if (Instant.now().getEpochSecond() > tokenData.expiresAt) {
            refreshTokens.remove(refreshToken);
            return Optional.empty();
        }

        // Generate new access token
        String newAccessToken = generateAccessToken(tokenData.teamId, tokenData.gameId);

        return Optional.of(new TokenResult(
            newAccessToken,
            refreshToken,
            3600,
            tokenData.teamId.toString(),
            tokenData.gameId.toString()
        ));
    }

    @Override
    public Optional<UUID> validateAccessToken(String accessToken) {
        if (accessToken == null) {
            return Optional.empty();
        }

        // Check in-memory store first
        AccessTokenData tokenData = accessTokens.get(accessToken);
        if (tokenData != null) {
            if (Instant.now().getEpochSecond() > tokenData.expiresAt) {
                accessTokens.remove(accessToken);
                return Optional.empty();
            }
            return Optional.of(tokenData.teamId);
        }

        // Fallback: parse mock token format (access_teamId_timestamp)
        if (accessToken.startsWith("access_")) {
            String[] parts = accessToken.split("_");
            if (parts.length >= 2) {
                try {
                    return Optional.of(UUID.fromString(parts[1]));
                } catch (IllegalArgumentException e) {
                    // Not a valid UUID
                }
            }
        }

        return Optional.empty();
    }

    @Override
    public void revokeTokens(UUID teamId) {
        // Remove all tokens for the team
        accessTokens.entrySet().removeIf(e -> e.getValue().teamId.equals(teamId));
        refreshTokens.entrySet().removeIf(e -> e.getValue().teamId.equals(teamId));
    }

    private String generateAccessToken(UUID teamId, UUID gameId) {
        // In production, use JWT. For now, use a simple format.
        String token = "access_" + teamId + "_" + System.currentTimeMillis();
        long expiresAt = Instant.now().plusSeconds(3600).getEpochSecond(); // 1 hour
        accessTokens.put(token, new AccessTokenData(teamId, gameId, expiresAt));
        return token;
    }

    private String generateRefreshToken(UUID teamId, UUID gameId) {
        String token = "refresh_" + teamId + "_" + UUID.randomUUID();
        long expiresAt = Instant.now().plusSeconds(604800).getEpochSecond(); // 7 days
        refreshTokens.put(token, new RefreshTokenData(teamId, gameId, expiresAt));
        return token;
    }

    private void cleanupExpiredTokens() {
        long now = Instant.now().getEpochSecond();
        registrationTokens.entrySet().removeIf(e -> e.getValue().expiresAt < now);
        refreshTokens.entrySet().removeIf(e -> e.getValue().expiresAt < now);
        accessTokens.entrySet().removeIf(e -> e.getValue().expiresAt < now);
    }
}
