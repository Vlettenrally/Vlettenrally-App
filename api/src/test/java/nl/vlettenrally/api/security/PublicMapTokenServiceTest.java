package nl.vlettenrally.api.security;

import org.junit.jupiter.api.Test;

import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Unit tests for {@link PublicMapTokenService}: derivation is stable and game-scoped, and everything
 * fails closed when no secret is configured.
 */
class PublicMapTokenServiceTest {

    private static final UUID GAME = UUID.fromString("a1b2c3d4-e5f6-7890-abcd-ef1234567890");
    private static final UUID OTHER_GAME = UUID.fromString("11111111-1111-1111-1111-111111111111");

    private PublicMapTokenService service(String configuredSecret) {
        PublicMapTokenService s = new PublicMapTokenService();
        // PublicEndpointConfig is a single-method interface, so a lambda stands in for it.
        s.config = () -> Optional.ofNullable(configuredSecret);
        return s;
    }

    @Test
    void tokenIsStableAcrossCalls() {
        PublicMapTokenService service = service("secret");

        assertEquals(service.tokenFor(GAME), service.tokenFor(GAME));
    }

    @Test
    void tokenIsStableAcrossInstances() {
        // A link handed out today must keep working after a restart — that is the whole point of
        // deriving rather than storing.
        assertEquals(service("secret").tokenFor(GAME), service("secret").tokenFor(GAME));
    }

    @Test
    void tokenDiffersPerGame() {
        PublicMapTokenService service = service("secret");

        assertNotEquals(service.tokenFor(GAME), service.tokenFor(OTHER_GAME));
    }

    @Test
    void tokenDiffersPerSecret() {
        assertNotEquals(service("secret").tokenFor(GAME), service("rotated").tokenFor(GAME));
    }

    @Test
    void tokenIsUrlSafeAndUnpadded() {
        String token = service("secret").tokenFor(GAME).orElseThrow();

        // It travels in a query string, so it must survive without escaping.
        assertTrue(token.matches("[A-Za-z0-9_-]+"), "not url-safe: " + token);
    }

    @Test
    void noSecret_disablesEverything() {
        PublicMapTokenService service = service(null);

        assertFalse(service.isEnabled());
        assertTrue(service.tokenFor(GAME).isEmpty());
        assertFalse(service.matches(GAME, "anything"));
    }

    @Test
    void blankSecret_disablesEverything() {
        PublicMapTokenService service = service("   ");

        assertFalse(service.isEnabled());
        assertTrue(service.tokenFor(GAME).isEmpty());
    }

    @Test
    void matchesOwnToken() {
        PublicMapTokenService service = service("secret");

        assertTrue(service.matches(GAME, service.tokenFor(GAME).orElseThrow()));
    }

    @Test
    void rejectsTokenMintedForAnotherGame() {
        PublicMapTokenService service = service("secret");
        String otherGamesToken = service.tokenFor(OTHER_GAME).orElseThrow();

        assertFalse(service.matches(GAME, otherGamesToken));
    }

    @Test
    void rejectsTokenFromRotatedSecret() {
        String oldToken = service("secret").tokenFor(GAME).orElseThrow();

        assertFalse(service("rotated").matches(GAME, oldToken));
    }

    @Test
    void rejectsTheSharedSecretItself() {
        // Holding the gallery secret must not be enough to open a map — only a derived token is.
        assertFalse(service("secret").matches(GAME, "secret"));
    }

    @Test
    void rejectsBlankAndNull() {
        PublicMapTokenService service = service("secret");

        assertFalse(service.matches(GAME, null));
        assertFalse(service.matches(GAME, "   "));
    }

    @Test
    void toleratesSurroundingWhitespace() {
        PublicMapTokenService service = service("secret");
        String token = service.tokenFor(GAME).orElseThrow();

        assertTrue(service.matches(GAME, "  " + token + "  "));
    }

    @Test
    void nullGame_hasNoToken() {
        assertTrue(service("secret").tokenFor(null).isEmpty());
    }
}
