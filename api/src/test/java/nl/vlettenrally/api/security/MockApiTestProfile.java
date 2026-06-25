package nl.vlettenrally.api.security;

import java.util.Map;

import io.quarkus.test.junit.QuarkusTestProfile;

/**
 * Boots the application on the in-memory mock repositories (active in the {@code test}
 * build profile) without any Docker-backed infrastructure, so authorization wiring can be
 * exercised end-to-end without a database, Flyway, or a real Valkey/Redis instance.
 */
public class MockApiTestProfile implements QuarkusTestProfile {

    @Override
    public Map<String, String> getConfigOverrides() {
        return Map.ofEntries(
            // No Docker: this harness only exercises authorization on mock repositories.
            Map.entry("quarkus.devservices.enabled", "false"),
            // Mock repositories are used in the test profile; no JPA/datasource needed.
            Map.entry("quarkus.hibernate-orm.enabled", "false"),
            Map.entry("quarkus.datasource.devservices.enabled", "false"),
            Map.entry("quarkus.flyway.enabled", "false"),
            Map.entry("quarkus.flyway.migrate-at-start", "false"),
            // Don't spin up a Redis dev service; the client is wired but unused by these tests.
            Map.entry("quarkus.redis.devservices.enabled", "false"),
            Map.entry("quarkus.redis.hosts", "redis://localhost:6379"),
            // External integration credentials have no defaults; supply dummies so startup
            // config validation passes (the integrations themselves are never exercised here).
            Map.entry("vlettenrally.firebase.credentials-path", "/dev/null"),
            Map.entry("vlettenrally.twilio.account-sid", "ACtest"),
            Map.entry("vlettenrally.twilio.api-key-sid", "SKtest"),
            Map.entry("vlettenrally.twilio.api-key-secret", "test-secret"),
            Map.entry("vlettenrally.twilio.from-number", "+10000000000"),
            // Test JWT keys so tokens can be minted/verified through the real auth pipeline
            // (which is what runs the StaffRoleAugmentor).
            Map.entry("smallrye.jwt.sign.key.location", "privateKey.pem"),
            Map.entry("mp.jwt.verify.publickey.location", "publicKey.pem"),
            Map.entry("mp.jwt.verify.issuer", "vlettenrally")
        );
    }
}
