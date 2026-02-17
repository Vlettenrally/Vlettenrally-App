package nl.vlettenrally.websockets;

import java.security.SecureRandom;
import java.time.Duration;
import java.util.Base64;
import java.util.Optional;
import java.util.UUID;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import io.quarkus.logging.Log;
import io.quarkus.redis.datasource.ReactiveRedisDataSource;
import io.quarkus.redis.datasource.value.ReactiveValueCommands;
import io.smallrye.mutiny.Uni;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import nl.vlettenrally.data.domain.Team;
import nl.vlettenrally.data.repository.TeamRepository;

/**
 * Service for generating and validating short-lived WebSocket connection tokens.
 * <p>
 * These tokens are used to authenticate WebSocket connections without relying on
 * {@code @RolesAllowed} annotations, which can interfere with session registration.
 * <p>
 * Flow:
 * 1. Client calls REST endpoint with valid JWT to obtain a WebSocket token
 * 2. Client connects to WebSocket with the token as a query parameter
 * 3. WebSocket endpoint validates the token and extracts session metadata
 */
@ApplicationScoped
public class WebSocketTokenService {

    private static final String TOKEN_KEY_PREFIX = "ws:token:";
    private static final Duration TOKEN_TTL = Duration.ofSeconds(30); // Short-lived for security
    private static final int TOKEN_LENGTH = 32;

    private final SecureRandom secureRandom = new SecureRandom();

    @Inject
    ReactiveRedisDataSource redisDataSource;

    @Inject
    ObjectMapper objectMapper;

    @Inject
    TeamRepository teamRepository;

    /**
     * Generate a WebSocket token for an admin user.
     * @param adminId The admin's user ID
     * @param adminName The admin's display name
     * @return Uni containing the generated token
     */
    public Uni<String> generateAdminToken(UUID adminId, String adminName) {
        String token = generateSecureToken();
        WebSocketTokenData data = WebSocketTokenData.forAdmin(adminId, adminName);
        return storeToken(token, data).replaceWith(token);
    }

    /**
     * Generate a WebSocket token for a team after validating the team exists and belongs to the game.
     * @param teamId The team's ID
     * @param gameId The game ID the team should belong to
     * @return Uni containing the result (success with token, or error)
     */
    public Uni<WebSocketTokenResult> generateTeamToken(UUID teamId, UUID gameId) {
        return Uni.createFrom().item(() -> {
            Optional<Team> teamOpt = teamRepository.findById(teamId);

            if (teamOpt.isEmpty()) {
                Log.warnf("Team not found when generating WebSocket token: %s", teamId);
                return new GenerateResult.Failure(new WebSocketTokenResult.TeamNotFound());
            }

            Team team = teamOpt.get();
            if (!team.getGameId().equals(gameId)) {
                Log.warnf("Team %s does not belong to game %s (actual: %s)", teamId, gameId, team.getGameId());
                return new GenerateResult.Failure(new WebSocketTokenResult.TeamGameMismatch());
            }

            String teamName = team.getName() != null ? team.getName() : "Team " + team.getBoatNumber();
            String token = generateSecureToken();
            WebSocketTokenData data = WebSocketTokenData.forTeam(teamId, teamName, gameId);

            try {
                String key = TOKEN_KEY_PREFIX + token;
                String json = objectMapper.writeValueAsString(data);
                return new GenerateResult.Pending(token, key, json);
            } catch (JsonProcessingException e) {
                Log.errorf("Failed to serialize WebSocket token data: %s", e.getMessage());
                return new GenerateResult.Failure(new WebSocketTokenResult.Error("Failed to generate token"));
            }
        }).flatMap(result -> switch (result) {
            case GenerateResult.Pending pending -> {
                ReactiveValueCommands<String, String> commands = redisDataSource.value(String.class);
                yield commands.setex(pending.key(), TOKEN_TTL.getSeconds(), pending.json())
                    .map(v -> (WebSocketTokenResult) new WebSocketTokenResult.Success(pending.token(), (int) TOKEN_TTL.getSeconds()))
                    .onFailure().recoverWithItem(error -> {
                        Log.errorf("Failed to store WebSocket token: %s", error.getMessage());
                        return new WebSocketTokenResult.Error("Failed to store token");
                    });
            }
            case GenerateResult.Failure failure -> Uni.createFrom().item(failure.result());
        });
    }

    // Internal sealed interface for token generation intermediate results
    private sealed interface GenerateResult {
        record Pending(String token, String key, String json) implements GenerateResult {}
        record Failure(WebSocketTokenResult result) implements GenerateResult {}
    }

    /**
     * Validate a WebSocket token and retrieve the associated data.
     * The token is consumed (deleted) after successful validation.
     * @param token The token to validate
     * @return Uni containing the token data, or null if invalid/expired
     */
    public Uni<WebSocketTokenData> validateAndConsumeToken(String token) {
        if (token == null || token.isBlank()) {
            return Uni.createFrom().nullItem();
        }

        String key = TOKEN_KEY_PREFIX + token;
        ReactiveValueCommands<String, String> commands = redisDataSource.value(String.class);

        // Get and delete atomically using GETDEL
        return commands.getdel(key)
            .onItem().transform(json -> {
                if (json == null) {
                    Log.debugf("WebSocket token not found or expired: %s", maskToken(token));
                    return null;
                }
                try {
                    WebSocketTokenData data = objectMapper.readValue(json, WebSocketTokenData.class);
                    Log.infof("WebSocket token validated for %s user: %s", data.role(), data.userName());
                    return data;
                } catch (JsonProcessingException e) {
                    Log.errorf("Failed to deserialize WebSocket token data: %s", e.getMessage());
                    return null;
                }
            })
            .onFailure().recoverWithItem(error -> {
                Log.errorf("Failed to validate WebSocket token: %s", error.getMessage());
                return null;
            });
    }

    /**
     * Validate a WebSocket token without consuming it (for checking validity).
     * @param token The token to validate
     * @return Uni containing the token data, or null if invalid/expired
     */
    public Uni<WebSocketTokenData> validateToken(String token) {
        if (token == null || token.isBlank()) {
            return Uni.createFrom().nullItem();
        }

        String key = TOKEN_KEY_PREFIX + token;
        ReactiveValueCommands<String, String> commands = redisDataSource.value(String.class);

        return commands.get(key)
            .onItem().transform(json -> {
                if (json == null) {
                    return null;
                }
                try {
                    return objectMapper.readValue(json, WebSocketTokenData.class);
                } catch (JsonProcessingException e) {
                    Log.errorf("Failed to deserialize WebSocket token data: %s", e.getMessage());
                    return null;
                }
            })
            .onFailure().recoverWithItem(error -> {
                Log.errorf("Failed to validate WebSocket token: %s", error.getMessage());
                return null;
            });
    }

    private Uni<Void> storeToken(String token, WebSocketTokenData data) {
        try {
            String key = TOKEN_KEY_PREFIX + token;
            String json = objectMapper.writeValueAsString(data);

            ReactiveValueCommands<String, String> commands = redisDataSource.value(String.class);
            return commands.setex(key, TOKEN_TTL.getSeconds(), json)
                .onItem().invoke(() -> Log.debugf("Stored WebSocket token for %s: %s", data.role(), data.userName()))
                .onFailure().invoke(error -> Log.errorf("Failed to store WebSocket token: %s", error.getMessage()))
                .replaceWithVoid();
        } catch (JsonProcessingException e) {
            Log.errorf("Failed to serialize WebSocket token data: %s", e.getMessage());
            return Uni.createFrom().failure(e);
        }
    }

    private String generateSecureToken() {
        byte[] bytes = new byte[TOKEN_LENGTH];
        secureRandom.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private String maskToken(String token) {
        if (token == null || token.length() < 8) {
            return "***";
        }
        return token.substring(0, 4) + "..." + token.substring(token.length() - 4);
    }
}

