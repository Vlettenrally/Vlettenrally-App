package nl.vlettenrally.service;

import io.quarkus.redis.datasource.RedisDataSource;
import io.quarkus.redis.datasource.keys.KeyCommands;
import io.quarkus.redis.datasource.value.SetArgs;
import io.quarkus.redis.datasource.value.ValueCommands;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Behavioural tests for the Valkey-backed leader lease.
 * <p>
 * Acquire path: SET key ownerId NX EX 90 — true means we won the election.
 * Renew path: when NX fails but the key holds OUR id, refresh with XX EX.
 * Skip path: another instance holds the lock — we stand down.
 */
@ExtendWith(MockitoExtension.class)
class PollingLeaderTest {

    @Mock RedisDataSource redisDataSource;
    @Mock ValueCommands<String, String> values;
    @Mock KeyCommands<String> keys;

    @InjectMocks PollingLeader leader;

    @BeforeEach
    void setUp() {
        leader.lockEnabled = true;
        org.mockito.Mockito.lenient().when(redisDataSource.value(String.class)).thenReturn(values);
    }

    @Test
    void shortCircuitsToTrue_whenLockDisabled() {
        leader.lockEnabled = false;
        assertTrue(leader.tryAcquireOrRenew());
        verify(values, never()).setAndChanged(any(), any(), any());
    }

    @Test
    void acquiresLeaseWithNxEx() {
        when(values.setAndChanged(eq(PollingLeader.LOCK_KEY), eq(leader.getOwnerId()), any(SetArgs.class)))
            .thenReturn(true);

        assertTrue(leader.tryAcquireOrRenew());

        ArgumentCaptor<SetArgs> args = ArgumentCaptor.forClass(SetArgs.class);
        verify(values).setAndChanged(eq(PollingLeader.LOCK_KEY), eq(leader.getOwnerId()), args.capture());
        // Verify we passed NX and EX 90 by inspecting the raw arg list (SetArgs exposes toArgs()).
        List<Object> raw = args.getValue().toArgs();
        assertTrue(raw.contains("NX"), "must use SET NX semantics");
        assertTrue(raw.contains("EX"), "must use EX TTL semantics");
        boolean hasTtl = raw.stream().anyMatch(o ->
            (o instanceof Number n && n.longValue() == PollingLeader.LOCK_TTL_SECONDS)
                || String.valueOf(PollingLeader.LOCK_TTL_SECONDS).equals(String.valueOf(o)));
        assertTrue(hasTtl, "TTL must be 90s");
    }

    @Test
    void renewsLeaseWhenWeStillHoldIt() {
        when(values.setAndChanged(eq(PollingLeader.LOCK_KEY), eq(leader.getOwnerId()), any(SetArgs.class)))
            .thenReturn(false)  // NX call: key already exists
            .thenReturn(true);  // XX renewal call: key existed and was refreshed
        when(values.get(PollingLeader.LOCK_KEY)).thenReturn(leader.getOwnerId());

        assertTrue(leader.tryAcquireOrRenew());

        verify(values, org.mockito.Mockito.times(2))
            .setAndChanged(eq(PollingLeader.LOCK_KEY), eq(leader.getOwnerId()), any(SetArgs.class));
    }

    @Test
    void standsDownWhenAnotherInstanceHoldsTheLock() {
        when(values.setAndChanged(eq(PollingLeader.LOCK_KEY), eq(leader.getOwnerId()), any(SetArgs.class)))
            .thenReturn(false);
        when(values.get(PollingLeader.LOCK_KEY)).thenReturn("other-instance-id");

        assertFalse(leader.tryAcquireOrRenew());

        verify(values, org.mockito.Mockito.times(1))
            .setAndChanged(eq(PollingLeader.LOCK_KEY), eq(leader.getOwnerId()), any(SetArgs.class));
    }

    @Test
    void failsClosed_whenValkeyThrows() {
        when(values.setAndChanged(eq(PollingLeader.LOCK_KEY), eq(leader.getOwnerId()), any(SetArgs.class)))
            .thenThrow(new RuntimeException("valkey down"));

        assertFalse(leader.tryAcquireOrRenew(),
            "must skip polling rather than risk a split-brain double-poll when Valkey is unreachable");
    }

    @Test
    void releasesLockOnShutdown_onlyWhenWeOwnIt() {
        when(values.get(PollingLeader.LOCK_KEY)).thenReturn(leader.getOwnerId());
        when(redisDataSource.key()).thenReturn(keys);

        leader.release();

        verify(keys).del(PollingLeader.LOCK_KEY);
    }

    @Test
    void doesNotReleaseSomeoneElsesLock() {
        when(values.get(PollingLeader.LOCK_KEY)).thenReturn("other-instance-id");

        leader.release();

        verify(redisDataSource, never()).key();
    }

    @Test
    void lockKey_isStableAcrossInstances() {
        // Documenting the constant — changing it would silently bypass the lock for already-running instances.
        assertEquals("vlettenrally:scheduler:location-polling:leader", PollingLeader.LOCK_KEY);
    }
}
