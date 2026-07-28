package nl.vlettenrally.service;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import static org.mockito.ArgumentMatchers.any;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import io.quarkus.redis.datasource.RedisDataSource;
import io.quarkus.redis.datasource.value.ValueCommands;

/**
 * Unit tests for {@link BackfillThrottle#lockedTeams}, with Valkey mocked out.
 * <p>
 * The polling sweep asks this for every participant on every tick, so the contract that
 * matters is one round-trip for the whole batch — asking per team was the poller's largest
 * source of Valkey traffic — and failing open so a Valkey hiccup never stalls the poller.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class BackfillThrottleTest {

    @Mock
    RedisDataSource redisDataSource;

    @Mock
    ValueCommands<String, String> values;

    @InjectMocks
    BackfillThrottle throttle;

    private void stubValues() {
        when(redisDataSource.value(String.class)).thenReturn(values);
    }

    @Test
    void lockedTeams_returnsOnlyTeamsWithALockPresent() {
        stubValues();
        UUID locked = UUID.randomUUID();
        UUID free = UUID.randomUUID();
        when(values.mget(any(String[].class))).thenReturn(Map.of(
            BackfillThrottle.LOCK_KEY_PREFIX + locked, "1"));

        Set<UUID> result = throttle.lockedTeams(List.of(locked, free));

        assertEquals(Set.of(locked), result);
    }

    @Test
    void lockedTeams_usesASingleRoundTripForTheWholeBatch() {
        stubValues();
        List<UUID> teams = List.of(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID());
        when(values.mget(any(String[].class))).thenReturn(Map.of());

        throttle.lockedTeams(teams);

        ArgumentCaptor<String[]> captor = ArgumentCaptor.forClass(String[].class);
        verify(values).mget(captor.capture());
        assertEquals(3, captor.getValue().length);
    }

    @Test
    void lockedTeams_emptyInput_doesNotTouchValkey() {
        Set<UUID> result = throttle.lockedTeams(List.of());

        assertTrue(result.isEmpty());
        verify(redisDataSource, never()).value(String.class);
    }

    @Test
    void lockedTeams_onValkeyError_failsOpen() {
        // Matching every other Valkey caller here: a hiccup must never stall the poller.
        stubValues();
        when(values.mget(any(String[].class))).thenThrow(new RuntimeException("valkey down"));

        assertTrue(throttle.lockedTeams(List.of(UUID.randomUUID())).isEmpty());
    }
}
