package nl.vlettenrally.service;

import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import com.fasterxml.jackson.databind.ObjectMapper;

import io.quarkus.redis.datasource.RedisDataSource;
import io.quarkus.redis.datasource.keys.KeyCommands;
import io.quarkus.redis.datasource.value.SetArgs;
import io.quarkus.redis.datasource.value.ValueCommands;
import nl.vlettenrally.data.dto.auth.TeamInvite;

/**
 * Unit tests for {@link InviteCodeStore}, with Valkey mocked out.
 * <p>
 * The behaviours that matter here are the ones the QR flow depends on: codes are short and
 * unambiguous (they are the whole QR payload), they are NOT consumed on resolve (one QR is
 * scanned by several teammates), and their TTL slides on every use (resolve → nickname →
 * register-device is three requests and must not expire mid-flow).
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class InviteCodeStoreTest {

    @Mock
    RedisDataSource redisDataSource;

    @Mock
    ValueCommands<String, String> valueCommands;

    @Mock
    KeyCommands<String> keyCommands;

    @InjectMocks
    InviteCodeStore store;

    private final UUID teamId = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private final UUID gameId = UUID.fromString("a1b2c3d4-e5f6-7890-abcd-ef1234567890");

    private static final long TTL_SECONDS = 15 * 60L;

    @BeforeEach
    void setUp() {
        when(redisDataSource.value(String.class)).thenReturn(valueCommands);
        when(redisDataSource.key()).thenReturn(keyCommands);
        // @ConfigProperty is not applied under @InjectMocks; set the TTL explicitly.
        store.ttlMinutes = 15;
        store.objectMapper = new ObjectMapper();
    }

    /** No existing code for the team — the mint path. */
    private void noExistingCode() {
        when(valueCommands.get(InviteCodeStore.TEAM_KEY_PREFIX + teamId)).thenReturn(null);
        when(valueCommands.setAndChanged(anyString(), anyString(), any(SetArgs.class))).thenReturn(true);
    }

    @Test
    void issue_mintsShortUnambiguousCodeWithTtl() {
        noExistingCode();

        String code = store.issue(teamId, gameId);

        // 10 chars of Crockford base32 (no I/L/O/U) — short enough to keep the QR coarse,
        // and every character survives QR alphanumeric mode.
        assertTrue(code.matches("^[0-9A-HJKMNP-TV-Z]{10}$"), "unexpected code shape: " + code);

        ArgumentCaptor<String> value = ArgumentCaptor.forClass(String.class);
        verify(valueCommands).setAndChanged(eq(InviteCodeStore.CODE_KEY_PREFIX + code),
            value.capture(), any(SetArgs.class));
        assertTrue(value.getValue().contains(teamId.toString()));
        assertTrue(value.getValue().contains(gameId.toString()));

        // Team pointer written so the next call hands back this same code.
        verify(valueCommands).setex(InviteCodeStore.TEAM_KEY_PREFIX + teamId, TTL_SECONDS, code);
    }

    @Test
    void issue_reusesLiveCodeAndSlidesTtl() {
        when(valueCommands.get(InviteCodeStore.TEAM_KEY_PREFIX + teamId)).thenReturn("K7QM4X9BTD");
        when(keyCommands.exists(InviteCodeStore.CODE_KEY_PREFIX + "K7QM4X9BTD")).thenReturn(true);

        String code = store.issue(teamId, gameId);

        assertEquals("K7QM4X9BTD", code, "a live code must be reused, so the QR on screen stays stable");
        verify(valueCommands, never()).setAndChanged(anyString(), anyString(), any(SetArgs.class));
        verify(keyCommands).expire(InviteCodeStore.CODE_KEY_PREFIX + "K7QM4X9BTD", TTL_SECONDS);
        verify(keyCommands).expire(InviteCodeStore.TEAM_KEY_PREFIX + teamId, TTL_SECONDS);
    }

    @Test
    void issue_mintsFreshCodeWhenTeamPointerIsStale() {
        // Pointer survived but the code it names already expired.
        when(valueCommands.get(InviteCodeStore.TEAM_KEY_PREFIX + teamId)).thenReturn("DEADBEEF12");
        when(keyCommands.exists(InviteCodeStore.CODE_KEY_PREFIX + "DEADBEEF12")).thenReturn(false);
        when(valueCommands.setAndChanged(anyString(), anyString(), any(SetArgs.class))).thenReturn(true);

        String code = store.issue(teamId, gameId);

        assertNotEquals("DEADBEEF12", code);
        verify(valueCommands).setAndChanged(eq(InviteCodeStore.CODE_KEY_PREFIX + code),
            anyString(), any(SetArgs.class));
    }

    @Test
    void issue_throwsWhenValkeyIsDown() {
        when(valueCommands.get(anyString())).thenThrow(new RuntimeException("valkey down"));
        when(valueCommands.setAndChanged(anyString(), anyString(), any(SetArgs.class)))
            .thenThrow(new RuntimeException("valkey down"));

        // Fail closed: better a retryable error on the QR page than a code that resolves to nothing.
        assertThrows(IllegalStateException.class, () -> store.issue(teamId, gameId));
    }

    @Test
    void resolve_returnsInviteSlidesTtlAndDoesNotConsume() {
        String json = "{\"gameId\":\"" + gameId + "\",\"teamId\":\"" + teamId + "\"}";
        when(valueCommands.get(InviteCodeStore.CODE_KEY_PREFIX + "K7QM4X9BTD")).thenReturn(json);

        Optional<TeamInvite> invite = store.resolve("K7QM4X9BTD");

        assertTrue(invite.isPresent());
        assertEquals(teamId, invite.get().teamId());
        assertEquals(gameId, invite.get().gameId());

        // A scan is three requests (resolve, nickname, register-device) — the window must reopen.
        verify(keyCommands).expire(InviteCodeStore.CODE_KEY_PREFIX + "K7QM4X9BTD", TTL_SECONDS);
        verify(keyCommands).expire(InviteCodeStore.TEAM_KEY_PREFIX + teamId, TTL_SECONDS);

        // Several teammates scan the same QR, so resolving must never delete the code.
        verify(keyCommands, never()).del(anyString());
        verify(valueCommands, never()).getdel(anyString());
    }

    @Test
    void resolve_isCaseInsensitive() {
        String json = "{\"gameId\":\"" + gameId + "\",\"teamId\":\"" + teamId + "\"}";
        when(valueCommands.get(InviteCodeStore.CODE_KEY_PREFIX + "K7QM4X9BTD")).thenReturn(json);

        assertTrue(store.resolve("  k7qm4x9btd  ").isPresent(), "a hand-typed code should still resolve");
    }

    @Test
    void resolve_rejectsUnknownAndMalformedCodes() {
        when(valueCommands.get(anyString())).thenReturn(null);

        assertTrue(store.resolve("K7QM4X9BTD").isEmpty(), "unknown code");
        assertTrue(store.resolve(null).isEmpty());
        assertTrue(store.resolve("   ").isEmpty());
        assertTrue(store.resolve("TOO-SHORT").isEmpty());
        // I/L/O/U are not in the alphabet, so a code containing them cannot exist.
        assertTrue(store.resolve("IIIIIIIIII").isEmpty());

        // Malformed input must not even reach Valkey.
        verify(valueCommands, never()).get(InviteCodeStore.CODE_KEY_PREFIX + "IIIIIIIIII");
    }

    @Test
    void resolve_failsClosedWhenValkeyIsDown() {
        when(valueCommands.get(anyString())).thenThrow(new RuntimeException("valkey down"));

        assertTrue(store.resolve("K7QM4X9BTD").isEmpty(), "must not admit an unverified code");
    }

    @Test
    void ttlSeconds_derivesFromConfiguredMinutes() {
        store.ttlMinutes = 1;
        assertEquals(60L, store.ttlSeconds());
    }

    @Test
    void issue_survivesTeamPointerWriteFailure() {
        when(valueCommands.get(InviteCodeStore.TEAM_KEY_PREFIX + teamId)).thenReturn(null);
        when(valueCommands.setAndChanged(anyString(), anyString(), any(SetArgs.class))).thenReturn(true);
        doThrow(new RuntimeException("valkey hiccup"))
            .when(valueCommands).setex(anyString(), anyLong(), anyString());

        // The code key itself is live, so the invite works; only code stability is lost.
        String code = store.issue(teamId, gameId);
        assertTrue(code.matches("^[0-9A-HJKMNP-TV-Z]{10}$"));
    }
}
