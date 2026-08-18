package nl.vlettenrally.service;

import io.quarkus.logging.Log;
import io.quarkus.redis.datasource.RedisDataSource;
import io.quarkus.redis.datasource.ReactiveRedisDataSource;
import io.quarkus.redis.datasource.value.ValueCommands;
import io.quarkus.redis.datasource.value.ReactiveValueCommands;
import io.smallrye.mutiny.Uni;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.eclipse.microprofile.config.inject.ConfigProperty;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

/**
 * Cross-instance access-token revocation store backed by Valkey/Redis.
 * <p>
 * Access tokens are short-lived (default 15 min), but when an admin resets a team's
 * check-in or removes a single device we want every API pod to reject those tokens
 * immediately rather than waiting for natural expiry. The in-memory map version of
 * this only protected the pod that handled the admin call; this Valkey-backed version
 * fans out via the same Valkey instance the WebSocket pub/sub already uses.
 * <p>
 * Keys carry a TTL slightly longer than the access-token lifetime — after that, any
 * token that could have been affected has expired on its own, so the cutoff is moot.
 * <p>
 * On Valkey errors we fail open (treat as "not revoked") and log: this matches the
 * pre-revocation security posture, and is what every other Valkey caller in this
 * codebase does (see ValkeySessionStore, AdminTopicSubscriptionManager).
 * <p>
 * Reads return {@link Uni} and use the reactive Valkey client so they can run on the
 * Vert.x event loop — the revocation filter runs there. Writes happen on admin worker
 * threads, so they stay on the simpler blocking client.
 */
@ApplicationScoped
public class TokenRevocationStore {

    private static final String SUBJECT_KEY_PREFIX = "auth:revoke:sub:";
    private static final String DEVICE_KEY_PREFIX = "auth:revoke:dev:";

    @Inject
    RedisDataSource redisDataSource;

    @Inject
    ReactiveRedisDataSource reactiveRedisDataSource;

    @ConfigProperty(name = "vlettenrally.jwt.access-token-expiry-minutes", defaultValue = "60")
    int accessTokenExpiryMinutes;

    private ValueCommands<String, String> commands() {
        return redisDataSource.value(String.class);
    }

    private ReactiveValueCommands<String, String> reactiveCommands() {
        return reactiveRedisDataSource.value(String.class);
    }

    private long ttlSeconds() {
        // Keep the cutoff slightly longer than the access-token lifetime so a token
        // issued just before revocation can't outlive the cutoff.
        return accessTokenExpiryMinutes * 60L + 60L;
    }

    public void revokeSubject(String subject, Instant cutoff) {
        if (subject == null || cutoff == null) {
            return;
        }
        try {
            commands().setex(SUBJECT_KEY_PREFIX + subject, ttlSeconds(), String.valueOf(cutoff.getEpochSecond()));
        } catch (Exception e) {
            Log.errorf("Failed to persist subject revocation cutoff for %s: %s", subject, e.getMessage());
        }
    }

    public Uni<Optional<Instant>> getSubjectCutoff(String subject) {
        if (subject == null) {
            return Uni.createFrom().item(Optional.empty());
        }
        return reactiveCommands().get(SUBJECT_KEY_PREFIX + subject)
            .map(TokenRevocationStore::parseCutoff)
            .onFailure().recoverWithItem(e -> {
                Log.errorf("Failed to read subject revocation cutoff for %s: %s", subject, e.getMessage());
                return Optional.empty();
            });
    }

    public void revokeDevice(String subject, UUID deviceId, Instant cutoff) {
        if (subject == null || deviceId == null || cutoff == null) {
            return;
        }
        try {
            commands().setex(deviceKey(subject, deviceId), ttlSeconds(), String.valueOf(cutoff.getEpochSecond()));
        } catch (Exception e) {
            Log.errorf("Failed to persist device revocation cutoff for %s/%s: %s", subject, deviceId, e.getMessage());
        }
    }

    public Uni<Optional<Instant>> getDeviceCutoff(String subject, UUID deviceId) {
        if (subject == null || deviceId == null) {
            return Uni.createFrom().item(Optional.empty());
        }
        return reactiveCommands().get(deviceKey(subject, deviceId))
            .map(TokenRevocationStore::parseCutoff)
            .onFailure().recoverWithItem(e -> {
                Log.errorf("Failed to read device revocation cutoff for %s/%s: %s", subject, deviceId, e.getMessage());
                return Optional.empty();
            });
    }

    private static String deviceKey(String subject, UUID deviceId) {
        return DEVICE_KEY_PREFIX + subject + ":" + deviceId;
    }

    private static Optional<Instant> parseCutoff(String raw) {
        if (raw == null || raw.isBlank()) {
            return Optional.empty();
        }
        try {
            return Optional.of(Instant.ofEpochSecond(Long.parseLong(raw.trim())));
        } catch (NumberFormatException e) {
            return Optional.empty();
        }
    }
}
