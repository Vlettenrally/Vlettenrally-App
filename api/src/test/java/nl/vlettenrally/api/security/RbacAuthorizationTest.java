package nl.vlettenrally.api.security;

import static io.restassured.RestAssured.given;

import java.util.Set;
import java.util.UUID;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.TestProfile;
import io.smallrye.jwt.build.Jwt;

/**
 * End-to-end authorization harness booting the full REST stack on mock repositories.
 * <p>
 * Tokens are minted with a real signing key and presented as Bearer auth, so the genuine
 * authentication pipeline runs — including {@link StaffRoleAugmentor}, which only augments
 * identities produced by an identity provider (not {@code @TestSecurity}). This verifies that:
 * <ul>
 *   <li>any admin-type role gains the synthetic {@code staff} role and so reaches baseline
 *       endpoints (read games, exchange the admin websocket token);</li>
 *   <li>granular endpoints still gate on the specific capability role;</li>
 *   <li>{@code admin} acts as a superset everywhere except admin-user management, which is
 *       reserved for {@code super_admin}.</li>
 * </ul>
 */
@QuarkusTest
@TestProfile(MockApiTestProfile.class)
class RbacAuthorizationTest {

    /** Mint a signed JWT with a UUID subject (the app treats the subject as a user UUID). */
    private static String token(String... roles) {
        return Jwt.issuer("vlettenrally")
            .subject(UUID.randomUUID().toString())
            .claim("name", "Test User")
            .groups(Set.of(roles))
            .sign();
    }

    private static io.restassured.specification.RequestSpecification as(String... roles) {
        return given().header("Authorization", "Bearer " + token(roles));
    }

    // ---- Baseline read access: any admin-type role becomes "staff" ----

    @Test
    void gameMaster_canReadGames() {
        as("game_master").when().get("/api/v1/games").then().statusCode(200);
    }

    @Test
    void viewer_canReadGames() {
        as("viewer").when().get("/api/v1/games").then().statusCode(200);
    }

    @Test
    void team_canReadGames() {
        as("team").when().get("/api/v1/games").then().statusCode(200);
    }

    @Test
    void anonymous_cannotReadGames() {
        given().when().get("/api/v1/games").then().statusCode(401);
    }

    // ---- Baseline websocket token: the originally reported bug ----

    @Test
    void gameMaster_canExchangeAdminWebSocketToken() {
        // Reaching the handler (not 403) proves the staff role gates this endpoint. The body
        // needs Redis which is not wired here, so 200 or 500 is fine; only a 403 would fail.
        int status = as("game_master").when().get("/api/v1/ws/token/admin").then().extract().statusCode();
        Assertions.assertNotEquals(403, status,
            "game_master should pass authorization for the admin websocket token endpoint");
    }

    @Test
    void team_cannotExchangeAdminWebSocketToken() {
        as("team").when().get("/api/v1/ws/token/admin").then().statusCode(403);
    }

    // ---- Granular write capability: GAME_MASTER manages games ----

    @Test
    void gameMaster_canCreateGame() {
        as("game_master")
            .contentType("application/json")
            .body("{\"name\":\"Integration Test Game\"}")
            .when().post("/api/v1/games")
            .then().statusCode(201);
    }

    @Test
    void viewer_cannotCreateGame() {
        as("viewer")
            .contentType("application/json")
            .body("{\"name\":\"Should Fail\"}")
            .when().post("/api/v1/games")
            .then().statusCode(403);
    }

    // ---- The ranking CSV export carries admin-only team notes ----

    @Test
    void rankingRole_reachesRankingExport() {
        // 404 (unknown game) rather than 403 proves the role gate let the caller through.
        as("ranking").when().get("/api/v1/games/" + UUID.randomUUID() + "/rankings/export")
            .then().statusCode(404);
    }

    @Test
    void admin_reachesRankingExport() {
        as("admin").when().get("/api/v1/games/" + UUID.randomUUID() + "/rankings/export")
            .then().statusCode(404);
    }

    @Test
    void team_cannotExportRankings() {
        // Teams may read published standings, but never the export: it includes team notes.
        as("team").when().get("/api/v1/games/" + UUID.randomUUID() + "/rankings/export")
            .then().statusCode(403);
    }

    @Test
    void viewer_cannotExportRankings() {
        // Staff, but without the ranking capability that gates the standings page.
        as("viewer").when().get("/api/v1/games/" + UUID.randomUUID() + "/rankings/export")
            .then().statusCode(403);
    }

    // ---- Admin-user management is SUPER_ADMIN only (not even a plain admin) ----

    @Test
    void superAdmin_canListAdmins() {
        as("super_admin").when().get("/api/v1/admins").then().statusCode(200);
    }

    @Test
    void superAdmin_canCreateAdmin() {
        as("super_admin")
            .contentType("application/json")
            .body("{\"username\":\"new-admin\",\"password\":\"S3cret-password!\",\"roles\":[\"viewer\"]}")
            .when().post("/api/v1/admins")
            .then().statusCode(201);
    }

    @Test
    void admin_cannotManageAdmins() {
        // A plain admin is not a super_admin: must not list, create, or modify admins.
        as("admin").when().get("/api/v1/admins").then().statusCode(403);
        as("admin")
            .contentType("application/json")
            .body("{\"username\":\"x\",\"password\":\"S3cret-password!\",\"roles\":[\"viewer\"]}")
            .when().post("/api/v1/admins")
            .then().statusCode(403);
    }

    @Test
    void gameMaster_cannotListAdmins() {
        as("game_master").when().get("/api/v1/admins").then().statusCode(403);
    }

    @Test
    void viewer_cannotListAdmins() {
        as("viewer").when().get("/api/v1/admins").then().statusCode(403);
    }

    @Test
    void admin_cannotChangeAdminLifecycle() {
        // Deactivate/activate/delete and the delete precondition are equally super_admin-only.
        // 403 must come from the role gate, before the resource ever looks the admin up.
        UUID target = UUID.randomUUID();
        as("admin").when().put("/api/v1/admins/" + target + "/deactivate").then().statusCode(403);
        as("admin").when().put("/api/v1/admins/" + target + "/activate").then().statusCode(403);
        as("admin").when().get("/api/v1/admins/" + target + "/references").then().statusCode(403);
        as("admin").when().delete("/api/v1/admins/" + target).then().statusCode(403);
    }

    @Test
    void viewer_cannotChangeAdminLifecycle() {
        UUID target = UUID.randomUUID();
        as("viewer").when().put("/api/v1/admins/" + target + "/deactivate").then().statusCode(403);
        as("viewer").when().put("/api/v1/admins/" + target + "/activate").then().statusCode(403);
        as("viewer").when().get("/api/v1/admins/" + target + "/references").then().statusCode(403);
        as("viewer").when().delete("/api/v1/admins/" + target).then().statusCode(403);
    }

    @Test
    void superAdmin_reachesAdminLifecycleEndpoints() {
        // Proves the routes exist and the role gate lets super_admin through: an unknown
        // target gets 404 from the resource, not 403 from security.
        UUID unknown = UUID.randomUUID();
        as("super_admin").when().put("/api/v1/admins/" + unknown + "/deactivate").then().statusCode(404);
        as("super_admin").when().put("/api/v1/admins/" + unknown + "/activate").then().statusCode(404);
        as("super_admin").when().get("/api/v1/admins/" + unknown + "/references").then().statusCode(404);
        as("super_admin").when().delete("/api/v1/admins/" + unknown).then().statusCode(404);
    }
}
