package nl.vlettenrally.service;

import java.security.SecureRandom;
import java.util.Base64;
import java.util.Optional;
import java.util.UUID;

import io.quarkus.logging.Log;
import io.quarkus.redis.datasource.RedisDataSource;
import io.quarkus.redis.datasource.value.ValueCommands;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

/**
 * Valkey-backed store for one-time picture-archive download tokens.
 * <p>
 * The bulk download is a plain browser navigation, not an XHR, so it cannot carry an
 * {@code Authorization} header. Instead a moderator first exchanges their JWT for one of these
 * tokens and the browser then navigates to the download URL with the token in the query string.
 * The token is:
 * <ul>
 *   <li><b>short-lived</b> — {@value #TOKEN_TTL_SECONDS} seconds, just long enough to hand off
 *       from the click to the navigation, which keeps the Valkey working set negligible;</li>
 *   <li><b>single-use</b> — {@link #consume} deletes it as it reads it, so a token that leaks
 *       into a browser history or a proxy log is already spent;</li>
 *   <li><b>game-bound</b> — the game id is the stored value, so the download endpoint takes the
 *       scope from the token rather than from a query parameter the caller controls.</li>
 * </ul>
 * Fails closed: a Valkey problem means no token is issued and no download is authorised.
 */
@ApplicationScoped
public class PictureDownloadTokenService {

    static final String TOKEN_KEY_PREFIX = "pic:dl:";

    /** Only has to survive the round trip from the token response to the browser navigation. */
    static final int TOKEN_TTL_SECONDS = 60;

    private static final int TOKEN_BYTES = 32;

    private final SecureRandom secureRandom = new SecureRandom();

    @Inject
    RedisDataSource redisDataSource;

    /** Lifetime handed to callers so the frontend knows how long it may sit on a token. */
    public int ttlSeconds() {
        return TOKEN_TTL_SECONDS;
    }

    /**
     * Mint a token authorising one download of {@code gameId}'s approved pictures.
     *
     * @throws IllegalStateException when the token could not be stored — better a retryable
     *         error than a token that resolves to nothing.
     */
    public String issue(UUID gameId) {
        String token = generateToken();
        try {
            values().setex(TOKEN_KEY_PREFIX + token, TOKEN_TTL_SECONDS, gameId.toString());
        } catch (Exception e) {
            throw new IllegalStateException("Failed to store picture download token", e);
        }
        Log.infof("Picture download token issued - gameId: %s, ttlSeconds: %d", gameId, TOKEN_TTL_SECONDS);
        return token;
    }

    /**
     * Resolve a token to the game it authorises, consuming it in the process. Empty when the
     * token is unknown, already used or expired.
     */
    public Optional<UUID> consume(String token) {
        if (token == null || token.isBlank()) {
            return Optional.empty();
        }
        String gameId;
        try {
            gameId = values().getdel(TOKEN_KEY_PREFIX + token);
        } catch (Exception e) {
            Log.errorf("Picture download token lookup failed: %s", e.getMessage());
            return Optional.empty();
        }
        if (gameId == null) {
            return Optional.empty();
        }
        try {
            return Optional.of(UUID.fromString(gameId));
        } catch (IllegalArgumentException e) {
            Log.errorf("Picture download token held a malformed game id: %s", gameId);
            return Optional.empty();
        }
    }

    private ValueCommands<String, String> values() {
        return redisDataSource.value(String.class);
    }

    /** URL-safe so the token can go straight into the download link without escaping. */
    private String generateToken() {
        byte[] bytes = new byte[TOKEN_BYTES];
        secureRandom.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }
}
