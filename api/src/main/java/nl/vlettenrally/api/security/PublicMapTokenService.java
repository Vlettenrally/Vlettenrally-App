package nl.vlettenrally.api.security;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import nl.vlettenrally.config.PublicEndpointConfig;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.util.Base64;
import java.util.Optional;
import java.util.UUID;

/**
 * Derives and verifies the per-game tokens that unlock the public narrowcasting map
 * ({@link nl.vlettenrally.api.v1.PublicMapResource}).
 * <p>
 * A token is {@code base64url(HMAC-SHA256(publicEndpointSecret, "map:" + gameId))}. Deriving rather
 * than storing means there is nothing to persist, expire or clean up: the link an admin hands to a
 * narrowcasting display keeps working for as long as the secret does, and rotating
 * {@code vlettenrally.public-endpoint.token} invalidates every link at once.
 * <p>
 * Two properties matter for why it is scoped this way. The token is <em>per game</em>, so a link that
 * leaks (it lives in a URL on a public display) exposes only that game's map — not the shared secret
 * itself, and therefore not the gallery or scoreboard endpoints that secret also guards. And the
 * {@value #MAP_PURPOSE} prefix is domain separation: if another per-resource public link is added
 * later it derives from a different message, so a token minted for one purpose can never validate
 * for another.
 */
@ApplicationScoped
public class PublicMapTokenService {

    private static final String HMAC_ALGORITHM = "HmacSHA256";

    /** Purpose prefix mixed into the HMAC message. See the class javadoc on domain separation. */
    private static final String MAP_PURPOSE = "map:";

    @Inject
    PublicEndpointConfig config;

    /**
     * Whether a secret is configured at all. When false the public map is disabled entirely: no link
     * can be minted and every request is rejected. Callers use this to tell "not enabled" apart from
     * "wrong token", which are the same outcome but very different things to report.
     */
    public boolean isEnabled() {
        return secret().isPresent();
    }

    /**
     * The public map token for a game, or empty when no secret is configured — in which case the
     * public map is disabled entirely and no link can be minted (fail closed).
     */
    public Optional<String> tokenFor(UUID gameId) {
        if (gameId == null) {
            return Optional.empty();
        }
        return secret().map(secret -> derive(secret, gameId));
    }

    /**
     * Whether {@code presented} is the valid token for {@code gameId}. False when no secret is
     * configured, when either argument is missing, or on any mismatch — including a token that is
     * valid but was derived for a different game.
     */
    public boolean matches(UUID gameId, String presented) {
        if (presented == null || presented.isBlank()) {
            return false;
        }
        return tokenFor(gameId)
            .map(expected -> constantTimeEquals(presented.trim(), expected))
            .orElse(false);
    }

    /** The configured shared secret, normalised so blank is treated the same as unset. */
    private Optional<String> secret() {
        return config.token().map(String::trim).filter(t -> !t.isEmpty());
    }

    private static String derive(String secret, UUID gameId) {
        try {
            Mac mac = Mac.getInstance(HMAC_ALGORITHM);
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), HMAC_ALGORITHM));
            byte[] signature = mac.doFinal((MAP_PURPOSE + gameId).getBytes(StandardCharsets.UTF_8));
            return Base64.getUrlEncoder().withoutPadding().encodeToString(signature);
        } catch (GeneralSecurityException e) {
            // HmacSHA256 is required of every JRE, so this is unreachable outside a broken runtime.
            throw new IllegalStateException("HMAC-SHA256 is unavailable", e);
        }
    }

    /** Constant-time comparison to avoid leaking the expected token via response timing. */
    private static boolean constantTimeEquals(String a, String b) {
        return MessageDigest.isEqual(
            a.getBytes(StandardCharsets.UTF_8),
            b.getBytes(StandardCharsets.UTF_8));
    }
}
