package nl.vlettenrally.service;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import static org.mockito.ArgumentMatchers.eq;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import org.mockito.junit.jupiter.MockitoExtension;

import io.quarkus.redis.datasource.RedisDataSource;
import io.quarkus.redis.datasource.ReactiveRedisDataSource;
import io.quarkus.redis.datasource.value.ValueCommands;
import io.quarkus.redis.datasource.value.ReactiveValueCommands;
import io.smallrye.mutiny.Uni;

/**
 * Unit tests for TokenRevocationStore.
 *
 * Reads go through the reactive Valkey client (so they can run on the event loop) and
 * fail open on errors; writes go through the blocking client. Both are mocked here.
 */
@ExtendWith(MockitoExtension.class)
class TokenRevocationStoreTest {

    @Mock
    RedisDataSource redisDataSource;

    @Mock
    ValueCommands<String, String> valueCommands;

    @Mock
    ReactiveRedisDataSource reactiveRedisDataSource;

    @Mock
    ReactiveValueCommands<String, String> reactiveValueCommands;

    @InjectMocks
    TokenRevocationStore store;

    private final String subject = UUID.randomUUID().toString();
    private final UUID deviceId = UUID.randomUUID();
    private final Instant cutoff = Instant.ofEpochSecond(1_700_000_000L);

    @BeforeEach
    void setUp() {
        // @ConfigProperty is not applied under @InjectMocks; set the lifetime explicitly
        // so ttlSeconds() is deterministic (15 * 60 + 60 = 960).
        store.accessTokenExpiryMinutes = 15;
    }

    // ---- reads: getSubjectCutoff ----

    @Test
    void getSubjectCutoff_storedValue_returnsInstant() {
        when(reactiveRedisDataSource.value(String.class)).thenReturn(reactiveValueCommands);
        when(reactiveValueCommands.get("auth:revoke:sub:" + subject))
            .thenReturn(Uni.createFrom().item(String.valueOf(cutoff.getEpochSecond())));

        Optional<Instant> result = store.getSubjectCutoff(subject)
            .await().atMost(Duration.ofSeconds(2));

        assertTrue(result.isPresent());
        assertEquals(cutoff, result.get());
    }

    @Test
    void getSubjectCutoff_missingKey_returnsEmpty() {
        when(reactiveRedisDataSource.value(String.class)).thenReturn(reactiveValueCommands);
        when(reactiveValueCommands.get("auth:revoke:sub:" + subject))
            .thenReturn(Uni.createFrom().nullItem());

        Optional<Instant> result = store.getSubjectCutoff(subject)
            .await().atMost(Duration.ofSeconds(2));

        assertFalse(result.isPresent());
    }

    @Test
    void getSubjectCutoff_malformedValue_returnsEmpty() {
        when(reactiveRedisDataSource.value(String.class)).thenReturn(reactiveValueCommands);
        when(reactiveValueCommands.get("auth:revoke:sub:" + subject))
            .thenReturn(Uni.createFrom().item("not-a-number"));

        Optional<Instant> result = store.getSubjectCutoff(subject)
            .await().atMost(Duration.ofSeconds(2));

        assertFalse(result.isPresent());
    }

    @Test
    void getSubjectCutoff_valkeyError_failsOpen() {
        when(reactiveRedisDataSource.value(String.class)).thenReturn(reactiveValueCommands);
        when(reactiveValueCommands.get("auth:revoke:sub:" + subject))
            .thenReturn(Uni.createFrom().failure(new RuntimeException("Connection refused")));

        Optional<Instant> result = store.getSubjectCutoff(subject)
            .await().atMost(Duration.ofSeconds(2));

        assertFalse(result.isPresent());
    }

    @Test
    void getSubjectCutoff_nullSubject_returnsEmptyWithoutValkey() {
        Optional<Instant> result = store.getSubjectCutoff(null)
            .await().atMost(Duration.ofSeconds(2));

        assertFalse(result.isPresent());
        verify(reactiveRedisDataSource, never()).value(String.class);
    }

    // ---- reads: getDeviceCutoff ----

    @Test
    void getDeviceCutoff_storedValue_returnsInstantFromDeviceKey() {
        when(reactiveRedisDataSource.value(String.class)).thenReturn(reactiveValueCommands);
        when(reactiveValueCommands.get("auth:revoke:dev:" + subject + ":" + deviceId))
            .thenReturn(Uni.createFrom().item(String.valueOf(cutoff.getEpochSecond())));

        Optional<Instant> result = store.getDeviceCutoff(subject, deviceId)
            .await().atMost(Duration.ofSeconds(2));

        assertTrue(result.isPresent());
        assertEquals(cutoff, result.get());
    }

    @Test
    void getDeviceCutoff_nullDeviceId_returnsEmptyWithoutValkey() {
        Optional<Instant> result = store.getDeviceCutoff(subject, null)
            .await().atMost(Duration.ofSeconds(2));

        assertFalse(result.isPresent());
        verify(reactiveRedisDataSource, never()).value(String.class);
    }

    // ---- writes: revokeSubject / revokeDevice (blocking client) ----

    @Test
    void revokeSubject_persistsCutoffWithTtl() {
        when(redisDataSource.value(String.class)).thenReturn(valueCommands);

        store.revokeSubject(subject, cutoff);

        verify(valueCommands).setex(
            eq("auth:revoke:sub:" + subject),
            eq(960L),
            eq(String.valueOf(cutoff.getEpochSecond())));
    }

    @Test
    void revokeDevice_persistsCutoffUnderDeviceKey() {
        when(redisDataSource.value(String.class)).thenReturn(valueCommands);

        store.revokeDevice(subject, deviceId, cutoff);

        verify(valueCommands).setex(
            eq("auth:revoke:dev:" + subject + ":" + deviceId),
            eq(960L),
            eq(String.valueOf(cutoff.getEpochSecond())));
    }

    @Test
    void revokeSubject_nullArguments_doNotTouchValkey() {
        store.revokeSubject(null, cutoff);
        store.revokeSubject(subject, null);

        verify(redisDataSource, never()).value(String.class);
    }
}
