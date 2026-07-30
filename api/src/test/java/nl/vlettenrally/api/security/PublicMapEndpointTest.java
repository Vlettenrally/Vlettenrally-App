package nl.vlettenrally.api.security;

import static io.restassured.RestAssured.given;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

import org.hamcrest.Matchers;
import org.junit.jupiter.api.Test;

import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.QuarkusTestProfile;
import io.quarkus.test.junit.TestProfile;
import io.smallrye.jwt.build.Jwt;
import jakarta.inject.Inject;

/**
 * End-to-end test for the public narrowcasting map, booting the real REST stack on mock
 * repositories.
 * <p>
 * The unit tests around {@link PublicMapTokenFilter} prove the comparison logic in isolation; what
 * only a running stack can prove is that the JAX-RS wiring actually holds together: that the name
 * binding activates the filter, that it resolves the {@code {gameId}} path parameter, and — the one
 * that would silently break everything — that a request carrying no {@code Authorization} header
 * reaches the resource at all rather than being rejected by SmallRye's proactive authentication
 * before any filter runs. That last point is exactly why the token travels in its own header.
 */
@QuarkusTest
@TestProfile(PublicMapEndpointTest.PublicMapEnabledProfile.class)
class PublicMapEndpointTest {

    /** {@link MockApiTestProfile} plus a configured public-endpoint secret. */
    public static class PublicMapEnabledProfile implements QuarkusTestProfile {
        static final String SECRET = "test-public-secret";

        @Override
        public Map<String, String> getConfigOverrides() {
            Map<String, String> overrides = new HashMap<>(new MockApiTestProfile().getConfigOverrides());
            overrides.put("vlettenrally.public-endpoint.token", SECRET);
            return overrides;
        }
    }

    private static final String TOKEN_HEADER = "X-Public-Map-Token";

    @Inject
    PublicMapTokenService tokenService;

    /** The game id the mock repositories are seeded with. */
    private UUID seededGameId() {
        return UUID.fromString(given()
            .header("Authorization", "Bearer " + staffToken())
            .when().get("/api/v1/games")
            .then().statusCode(200)
            .extract().path("[0].id"));
    }

    private static String staffToken() {
        return Jwt.issuer("vlettenrally")
            .subject(UUID.randomUUID().toString())
            .claim("name", "Test Admin")
            .groups(Set.of("admin"))
            .sign();
    }

    // ---- The token gate ----

    @Test
    void noHeader_isRejected() {
        // The critical case: an anonymous request must reach our filter and get its 401 from there,
        // not be swallowed by the JWT mechanism.
        given().when().get("/api/v1/public/games/" + seededGameId() + "/map")
            .then().statusCode(401)
            .body("message", Matchers.containsString(TOKEN_HEADER));
    }

    @Test
    void wrongToken_isRejected() {
        given().header(TOKEN_HEADER, "nonsense")
            .when().get("/api/v1/public/games/" + seededGameId() + "/map")
            .then().statusCode(401);
    }

    @Test
    void sharedSecretItself_isRejected() {
        // Holding the gallery/scoreboard secret must not open a map.
        given().header(TOKEN_HEADER, PublicMapEnabledProfile.SECRET)
            .when().get("/api/v1/public/games/" + seededGameId() + "/map")
            .then().statusCode(401);
    }

    @Test
    void tokenForAnotherGame_isRejected() {
        String otherGamesToken = tokenService.tokenFor(UUID.randomUUID()).orElseThrow();

        given().header(TOKEN_HEADER, otherGamesToken)
            .when().get("/api/v1/public/games/" + seededGameId() + "/map")
            .then().statusCode(401);
    }

    @Test
    void validToken_returnsTheMap() {
        UUID gameId = seededGameId();

        given().header(TOKEN_HEADER, tokenService.tokenFor(gameId).orElseThrow())
            .when().get("/api/v1/public/games/" + gameId + "/map")
            .then().statusCode(200)
            .body("gameId", Matchers.equalTo(gameId.toString()))
            .body("teams", Matchers.notNullValue())
            .body("zones", Matchers.notNullValue());
    }

    @Test
    void unknownGame_returns404WithItsOwnToken() {
        // A well-formed token for a game that does not exist gets past the gate and 404s, proving
        // the filter binds to the path rather than to any stored list.
        UUID unknown = UUID.randomUUID();

        given().header(TOKEN_HEADER, tokenService.tokenFor(unknown).orElseThrow())
            .when().get("/api/v1/public/games/" + unknown + "/map")
            .then().statusCode(404);
    }

    // ---- Hidden zones ----

    @Test
    void hiddenZonesAreNeverServed() {
        UUID gameId = seededGameId();
        String adminToken = staffToken();

        // Create a hidden zone through the real admin API.
        String hiddenId = given()
            .header("Authorization", "Bearer " + adminToken)
            .contentType("application/json")
            .body("""
                {"name":"Geheime zone","points":10,"latitude":52.1,"longitude":5.1,
                 "zoneType":"circle","radiusMeters":50,"hidden":true}
                """)
            .when().post("/api/v1/games/" + gameId + "/checkpoints")
            .then().statusCode(Matchers.anyOf(
                Matchers.is(200), Matchers.is(201)))
            .extract().path("id");

        // Staff see it...
        List<String> staffZoneIds = given()
            .header("Authorization", "Bearer " + adminToken)
            .when().get("/api/v1/games/" + gameId + "/checkpoints")
            .then().statusCode(200)
            .extract().path("id");
        assertTrue(staffZoneIds.contains(hiddenId), "staff should see the hidden zone");

        // ...the public map does not, and never carries a `hidden` field at all.
        var publicMap = given()
            .header(TOKEN_HEADER, tokenService.tokenFor(gameId).orElseThrow())
            .when().get("/api/v1/public/games/" + gameId + "/map")
            .then().statusCode(200)
            .extract().jsonPath();

        List<String> publicZoneIds = publicMap.getList("zones.id");
        assertFalse(publicZoneIds.contains(hiddenId), "hidden zone leaked onto the public map");

        List<Object> hiddenFlags = publicMap.getList("zones.hidden");
        assertTrue(hiddenFlags.stream().allMatch(Objects::isNull),
            "public zones must not carry a hidden field");
    }

    // ---- Admin link minting ----

    @Test
    void adminCanMintATokenThatWorks() {
        UUID gameId = seededGameId();

        String minted = given()
            .header("Authorization", "Bearer " + staffToken())
            .when().get("/api/v1/games/" + gameId + "/public-map-token")
            .then().statusCode(200)
            .extract().path("token");

        given().header(TOKEN_HEADER, minted)
            .when().get("/api/v1/public/games/" + gameId + "/map")
            .then().statusCode(200);
    }

    @Test
    void mintingRequiresStaff() {
        given().when().get("/api/v1/games/" + seededGameId() + "/public-map-token")
            .then().statusCode(401);
    }

    @Test
    void mintingUnknownGame_returns404() {
        given().header("Authorization", "Bearer " + staffToken())
            .when().get("/api/v1/games/" + UUID.randomUUID() + "/public-map-token")
            .then().statusCode(404);
    }
}
