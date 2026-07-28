package nl.vlettenrally.service;

import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import io.quarkus.redis.datasource.RedisDataSource;
import io.quarkus.redis.datasource.value.ValueCommands;

/**
 * Unit tests for {@link PictureDownloadTokenService}, with Valkey mocked out.
 * <p>
 * The properties that matter are the ones that make it safe to put a token in a URL: it is
 * consumed on first use, it carries the game scope itself, and it fails closed on anything
 * unexpected.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class PictureDownloadTokenServiceTest {

    @Mock
    RedisDataSource redisDataSource;

    @Mock
    ValueCommands<String, String> valueCommands;

    @InjectMocks
    PictureDownloadTokenService service;

    private final UUID gameId = UUID.fromString("a1b2c3d4-e5f6-7890-abcd-ef1234567890");

    @BeforeEach
    void setUp() {
        when(redisDataSource.value(String.class)).thenReturn(valueCommands);
    }

    @Test
    void issue_storesTheGameIdUnderAShortLivedKey() {
        String token = service.issue(gameId);

        ArgumentCaptor<String> key = ArgumentCaptor.forClass(String.class);
        verify(valueCommands).setex(key.capture(),
            eq((long) PictureDownloadTokenService.TOKEN_TTL_SECONDS), eq(gameId.toString()));
        assertEquals(PictureDownloadTokenService.TOKEN_KEY_PREFIX + token, key.getValue());
    }

    @Test
    void issue_producesUrlSafeUnpredictableTokens() {
        String first = service.issue(gameId);
        String second = service.issue(gameId);

        assertNotEquals(first, second);
        assertTrue(first.matches("[A-Za-z0-9_-]+"), "token must be URL-safe: " + first);
    }

    @Test
    void issue_failsWhenTheTokenCannotBeStored() {
        doThrow(new RuntimeException("valkey down"))
            .when(valueCommands).setex(anyString(), anyLong(), anyString());

        // Better a retryable error than a token that resolves to nothing.
        assertThrows(IllegalStateException.class, () -> service.issue(gameId));
    }

    @Test
    void consume_returnsTheGameAndSpendsTheToken() {
        when(valueCommands.getdel(PictureDownloadTokenService.TOKEN_KEY_PREFIX + "abc"))
            .thenReturn(gameId.toString());

        assertEquals(Optional.of(gameId), service.consume("abc"));
        // GETDEL, not GET: a second use of the same token finds nothing.
        verify(valueCommands).getdel(PictureDownloadTokenService.TOKEN_KEY_PREFIX + "abc");
    }

    @Test
    void consume_rejectsAnUnknownOrExpiredToken() {
        when(valueCommands.getdel(anyString())).thenReturn(null);

        assertTrue(service.consume("gone").isEmpty());
    }

    @Test
    void consume_rejectsABlankToken() {
        assertTrue(service.consume(null).isEmpty());
        assertTrue(service.consume("  ").isEmpty());
    }

    @Test
    void consume_failsClosedWhenValkeyIsUnreachable() {
        when(valueCommands.getdel(anyString())).thenThrow(new RuntimeException("valkey down"));

        assertFalse(service.consume("abc").isPresent());
    }
}
