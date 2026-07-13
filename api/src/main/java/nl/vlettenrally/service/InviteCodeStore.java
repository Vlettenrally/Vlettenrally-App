package nl.vlettenrally.service;

import java.security.SecureRandom;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;

import org.eclipse.microprofile.config.inject.ConfigProperty;

import com.fasterxml.jackson.databind.ObjectMapper;

import io.quarkus.logging.Log;
import io.quarkus.redis.datasource.RedisDataSource;
import io.quarkus.redis.datasource.keys.KeyCommands;
import io.quarkus.redis.datasource.value.SetArgs;
import io.quarkus.redis.datasource.value.ValueCommands;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import nl.vlettenrally.data.dto.auth.TeamInvite;

/**
 * Valkey-backed store for team invite codes — the short codes behind the "add another
 * device" QR.
 * <p>
 * The code is deliberately tiny (10 characters) because it is the entire QR payload: the
 * QR encodes {@code HTTPS://HOST/I/<CODE>}, which is short enough and — being uppercase
 * with no {@code ?} or {@code =} — qualifies for QR alphanumeric mode, keeping the symbol
 * down to ~29 modules. An earlier version embedded a signed JWT instead and produced a
 * ~750-character payload that phones could not reliably scan.
 * <p>
 * Two keys per invite:
 * <ul>
 *   <li>{@code invite:code:<CODE>} → the {@link TeamInvite} JSON the code resolves to.</li>
 *   <li>{@code invite:team:<teamId>} → the team's current code, so re-opening the QR page
 *       shows the same code instead of minting a new one on every mount.</li>
 * </ul>
 * Both carry the same TTL, which <b>slides</b>: every {@link #issue} and every successful
 * {@link #resolve} pushes expiry back out to the full window. A code therefore lives for
 * {@code invite-code-ttl-minutes} after it was last shown or scanned, which keeps the
 * Valkey working set small while ensuring a scan in progress (resolve → nickname →
 * register-device is three separate requests) cannot expire underneath the user.
 * <p>
 * Codes are multi-use within their TTL — one QR is scanned by several teammates in a row —
 * so {@link #resolve} never consumes them.
 * <p>
 * Unlike most Valkey callers in this codebase, this one fails <em>closed</em>: there is no
 * safe fallback for an auth-adjacent lookup, so a Valkey outage means invites stop working
 * rather than letting an unverified code through. (WebSocket connections already hard-depend
 * on Valkey, so this is not a new class of outage.)
 */
@ApplicationScoped
public class InviteCodeStore {

    static final String CODE_KEY_PREFIX = "invite:code:";
    static final String TEAM_KEY_PREFIX = "invite:team:";

    /**
     * Crockford base32: no I/L/O/U, so no character is visually ambiguous when read off a
     * screen, and every character is in the QR alphanumeric set.
     */
    private static final char[] ALPHABET = "0123456789ABCDEFGHJKMNPQRSTVWXYZ".toCharArray();

    /** 10 chars of a 32-symbol alphabet = 50 bits, the brute-force guard on the public resolve endpoint. */
    static final int CODE_LENGTH = 10;

    /** Codes are random, so a collision is effectively impossible; retry anyway rather than overwrite one. */
    private static final int MINT_ATTEMPTS = 3;

    private final SecureRandom secureRandom = new SecureRandom();

    @Inject
    RedisDataSource redisDataSource;

    @Inject
    ObjectMapper objectMapper;

    @ConfigProperty(name = "vlettenrally.checkin.invite-code-ttl-minutes", defaultValue = "15")
    int ttlMinutes;

    private ValueCommands<String, String> values() {
        return redisDataSource.value(String.class);
    }

    private KeyCommands<String> keys() {
        return redisDataSource.key();
    }

    /** Lifetime handed to callers so they can schedule a refresh before the code dies. */
    public long ttlSeconds() {
        return ttlMinutes * 60L;
    }

    /**
     * Return the team's current invite code, minting one if it has none (or its code expired).
     * Either way the TTL is pushed back out to the full window.
     *
     * @throws IllegalStateException when the code could not be stored — the caller surfaces this
     *         as an error the team can retry, rather than handing out a code that resolves to nothing
     */
    public String issue(UUID teamId, UUID gameId) {
        String existing = reuseExistingCode(teamId);
        if (existing != null) {
            return existing;
        }

        String json;
        try {
            json = objectMapper.writeValueAsString(new TeamInvite(gameId, teamId));
        } catch (Exception e) {
            throw new IllegalStateException("Failed to serialize invite for team " + teamId, e);
        }

        for (int attempt = 0; attempt < MINT_ATTEMPTS; attempt++) {
            String code = generateCode();
            boolean stored;
            try {
                stored = values().setAndChanged(CODE_KEY_PREFIX + code, json,
                    new SetArgs().nx().ex(ttlSeconds()));
            } catch (Exception e) {
                throw new IllegalStateException("Failed to store invite code for team " + teamId, e);
            }
            if (stored) {
                try {
                    values().setex(TEAM_KEY_PREFIX + teamId, ttlSeconds(), code);
                } catch (Exception e) {
                    // The code itself is live, so the invite works; we just lost the ability to
                    // hand back this same code on the next call and will mint a fresh one.
                    Log.warnf("Invite team-code pointer write failed for team %s: %s", teamId, e.getMessage());
                }
                Log.infof("Invite code issued - teamId: %s, gameId: %s, ttlSeconds: %d",
                    teamId, gameId, ttlSeconds());
                return code;
            }
        }
        throw new IllegalStateException("Failed to mint a unique invite code for team " + teamId);
    }

    /**
     * The team's still-live code with its TTL refreshed, or null when it has none and one must
     * be minted. A Valkey failure here is not fatal — we fall through to minting a fresh code.
     */
    private String reuseExistingCode(UUID teamId) {
        try {
            String code = values().get(TEAM_KEY_PREFIX + teamId);
            if (code == null || !keys().exists(CODE_KEY_PREFIX + code)) {
                return null;
            }
            slide(code, teamId);
            return code;
        } catch (Exception e) {
            Log.warnf("Invite code reuse lookup failed for team %s: %s — minting a fresh code",
                teamId, e.getMessage());
            return null;
        }
    }

    /**
     * Resolve a scanned code to the game + team it grants access to, sliding its TTL so the
     * rest of the check-in flow has the full window to complete. The code is not consumed.
     */
    public Optional<TeamInvite> resolve(String code) {
        String normalized = normalize(code);
        if (normalized == null) {
            return Optional.empty();
        }
        try {
            String json = values().get(CODE_KEY_PREFIX + normalized);
            if (json == null) {
                Log.debugf("Invite code not found or expired: %s", normalized);
                return Optional.empty();
            }
            TeamInvite invite = objectMapper.readValue(json, TeamInvite.class);
            slide(normalized, invite.teamId());
            return Optional.of(invite);
        } catch (Exception e) {
            Log.errorf("Invite code resolve failed for %s: %s", normalized, e.getMessage());
            return Optional.empty();
        }
    }

    /** Push both keys' expiry back out to the full window. Best-effort: the invite still works without it. */
    private void slide(String code, UUID teamId) {
        try {
            keys().expire(CODE_KEY_PREFIX + code, ttlSeconds());
            keys().expire(TEAM_KEY_PREFIX + teamId, ttlSeconds());
        } catch (Exception e) {
            Log.debugf("Invite code TTL refresh failed for %s: %s", code, e.getMessage());
        }
    }

    /**
     * Codes are uppercase by construction, but a hand-typed or hand-copied one may not be —
     * accept any case, and reject anything that is not a code at all.
     */
    private String normalize(String code) {
        if (code == null || code.isBlank()) {
            return null;
        }
        String normalized = code.trim().toUpperCase(Locale.ROOT);
        if (normalized.length() != CODE_LENGTH) {
            return null;
        }
        for (char c : normalized.toCharArray()) {
            if (indexOf(c) < 0) {
                return null;
            }
        }
        return normalized;
    }

    private static int indexOf(char c) {
        for (int i = 0; i < ALPHABET.length; i++) {
            if (ALPHABET[i] == c) {
                return i;
            }
        }
        return -1;
    }

    private String generateCode() {
        StringBuilder sb = new StringBuilder(CODE_LENGTH);
        for (int i = 0; i < CODE_LENGTH; i++) {
            sb.append(ALPHABET[secureRandom.nextInt(ALPHABET.length)]);
        }
        return sb.toString();
    }
}
