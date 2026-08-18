package nl.vlettenrally.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.TestProfile;
import io.smallrye.jwt.auth.principal.JWTParser;
import jakarta.inject.Inject;
import nl.vlettenrally.api.security.MockApiTestProfile;
import nl.vlettenrally.data.domain.Admin;
import nl.vlettenrally.data.domain.RefreshToken;
import nl.vlettenrally.data.domain.Team;
import nl.vlettenrally.data.dto.auth.RefreshOutcome;
import nl.vlettenrally.data.dto.auth.TokenResult;
import nl.vlettenrally.data.enums.Role;
import nl.vlettenrally.data.repository.AdminRepository;
import nl.vlettenrally.data.repository.RefreshTokenRepository;
import nl.vlettenrally.data.repository.TeamRepository;

/**
 * Refresh-token rotation behaviour.
 * <p>
 * The theme of most of these cases is that a client which lost a rotation response must converge
 * on the current live token rather than be treated as an attacker: teams share one subject across
 * every device they check in, so a subject-wide revocation strands a whole crew, and their only
 * way back in is a physical check-in at the rally desk.
 * <p>
 * Boots on mock repositories (no Docker) so the real rotation logic runs. Note that the mock
 * repository hands back live shared instances, so it cannot reproduce JPA first-level-cache
 * staleness — the concurrency case behind {@code findByIdFresh} has to be checked against a real
 * database (see the plan's verification steps).
 */
@QuarkusTest
@TestProfile(MockApiTestProfile.class)
class AuthServiceRefreshTokenTest {

    @Inject
    AuthService authService;

    @Inject
    TeamRepository teamRepository;

    @Inject
    AdminRepository adminRepository;

    @Inject
    RefreshTokenRepository refreshTokenRepository;

    @Inject
    JWTParser jwtParser;

    private Team anyTeam() {
        List<Team> teams = teamRepository.findAll();
        assertFalse(teams.isEmpty(), "mock profile should provide prefilled teams");
        return teams.getFirst();
    }

    /** A throwaway admin, so toggling `active` cannot disturb the seeded mock admins. */
    private Admin newAdmin(boolean active) {
        Admin admin = new Admin();
        admin.setId(UUID.randomUUID());
        admin.setUsername("refresh-test-" + UUID.randomUUID());
        admin.setPasswordHash("hashed");
        admin.setRoles(Set.of(Role.ADMIN));
        admin.setActive(active);
        return adminRepository.save(admin);
    }

    /** Persist a refresh token for an admin, the way the admin-login endpoint does. */
    private String issueAdminRefreshToken(Admin admin) {
        String refreshToken = authService.generateRefreshTokenValue();
        authService.storeRefreshToken(
            refreshToken,
            admin.getId().toString(),
            admin.getUsername(),
            Set.of("admin"),
            Instant.now().plusSeconds(86400).getEpochSecond());
        return refreshToken;
    }

    private TokenResult success(String refreshToken) {
        RefreshOutcome outcome = authService.refreshToken(refreshToken);
        return assertInstanceOf(RefreshOutcome.Success.class, outcome,
            "expected a successful refresh but got: " + outcome).tokens();
    }

    private void assertRetryable(String refreshToken, String because) {
        assertInstanceOf(RefreshOutcome.Retryable.class, authService.refreshToken(refreshToken), because);
    }

    private void assertRejected(String refreshToken, String because) {
        assertInstanceOf(RefreshOutcome.Rejected.class, authService.refreshToken(refreshToken), because);
    }

    /** Rotate the whole lineage forward one step, returning the new live token. */
    private String rotate(String refreshToken) {
        return success(refreshToken).refreshToken();
    }

    @Test
    void refresh_rotatesToNewPairAndRevokesOld() {
        Team team = anyTeam();
        TokenResult initial = authService.issueTeamTokens(team);

        TokenResult refreshed = success(initial.refreshToken());

        assertNotNull(refreshed.accessToken());
        assertNotEquals(initial.refreshToken(), refreshed.refreshToken(),
            "rotation must issue a new refresh token");
        assertTrue(refreshTokenRepository.findByToken(initial.refreshToken()).orElseThrow().isRevoked(),
            "the old refresh token must be revoked after rotation");
    }

    @Test
    void refresh_rotationPreservesFamilyAndDevice() {
        Team team = anyTeam();
        UUID deviceId = UUID.randomUUID();
        TokenResult initial = authService.issueTeamTokens(team, deviceId);
        RefreshToken initialRow = refreshTokenRepository.findByToken(initial.refreshToken()).orElseThrow();

        String successor = rotate(initial.refreshToken());
        RefreshToken successorRow = refreshTokenRepository.findByToken(successor).orElseThrow();

        assertEquals(initialRow.getFamilyId(), successorRow.getFamilyId(),
            "a rotation stays in the same lineage, otherwise revocation cannot be scoped to it");
        assertEquals(deviceId, successorRow.getDeviceId(),
            "a rotation must keep its device binding");
    }

    @Test
    void refresh_rotatedAccessTokenCarriesDeviceClaim() throws Exception {
        Team team = anyTeam();
        UUID deviceId = UUID.randomUUID();
        TokenResult initial = authService.issueTeamTokens(team, deviceId);

        TokenResult rotated = success(initial.refreshToken());

        // Without this the rotated token escapes TokenRevocationFilter's per-device check, and
        // "remove this device" silently stopped working one refresh after it was issued.
        assertEquals(deviceId.toString(), jwtParser.parse(rotated.accessToken()).getClaim("did"),
            "a rotated access token must keep its device claim");
    }

    @Test
    void refresh_lostResponseReplayReturnsLiveSuccessor() {
        Team team = anyTeam();
        TokenResult initial = authService.issueTeamTokens(team);

        String successor = rotate(initial.refreshToken());

        // The client never received the rotation response (dropped on a flaky network) and retries
        // the old token. It must get the SAME live successor back — no lockout, no revocation.
        TokenResult replay = success(initial.refreshToken());
        assertEquals(successor, replay.refreshToken(),
            "replay must return the existing live successor, not a new chain");
        assertNotNull(replay.accessToken(), "replay must still mint a usable access token");

        // The successor is genuinely live and can itself be rotated.
        assertNotNull(rotate(successor), "the successor token must remain usable");
    }

    @Test
    void refresh_replayTwoRotationsBehindReturnsCurrentLiveToken() {
        Team team = anyTeam();
        TokenResult initial = authService.issueTeamTokens(team);

        String second = rotate(initial.refreshToken());
        String third = rotate(second);

        // Only the immediate successor used to be followed, so a client two rotations behind fell
        // through to the revoke branch.
        assertEquals(third, success(initial.refreshToken()).refreshToken(),
            "a replay several rotations behind must converge on the current live token");
    }

    @Test
    void refresh_replayLongAfterWindowStillReturnsLiveHead() {
        Team team = anyTeam();
        String subject = team.getId().toString();
        TokenResult initial = authService.issueTeamTokens(team);

        String successor = rotate(initial.refreshToken());

        // A phone that was backgrounded for hours before retrying. The replay window used to gate
        // the idempotent path, so this was treated as an attack even with a live successor.
        RefreshToken old = refreshTokenRepository.findByToken(initial.refreshToken()).orElseThrow();
        old.setRevokedAt(Instant.now().minusSeconds(6 * 3600));
        refreshTokenRepository.save(old);

        assertEquals(successor, success(initial.refreshToken()).refreshToken(),
            "age alone must not turn a resolvable replay into an attack");
        assertFalse(refreshTokenRepository.findBySubject(subject).isEmpty(),
            "a stale-but-resolvable replay must revoke nothing");
    }

    @Test
    void refresh_missingSuccessorRowDoesNotRevokeAnything() {
        Team team = anyTeam();
        UUID deviceId = UUID.randomUUID();
        TokenResult initial = authService.issueTeamTokens(team, deviceId);

        String successor = rotate(initial.refreshToken());

        // The successor row is gone — expiry cleanup, a logout, a check-in reset. The lineage is
        // unresolvable, but that is not evidence of anything: let the client retry.
        RefreshToken old = refreshTokenRepository.findByToken(initial.refreshToken()).orElseThrow();
        old.setRevokedAt(Instant.now().minusSeconds(3600));
        refreshTokenRepository.save(old);
        refreshTokenRepository.deleteByToken(successor);

        assertRetryable(initial.refreshToken(), "an unresolvable chain must be retryable, not fatal");
        assertTrue(refreshTokenRepository.findByToken(initial.refreshToken()).isPresent(),
            "an unresolvable chain must not be revoked");
    }

    @Test
    void refresh_repeatedReplayOfTheSameTokenRevokesItsLineage() {
        Team team = anyTeam();
        UUID deviceId = UUID.randomUUID();
        TokenResult initial = authService.issueTeamTokens(team, deviceId);
        UUID familyId = refreshTokenRepository.findByToken(initial.refreshToken()).orElseThrow().getFamilyId();

        rotate(initial.refreshToken());

        // Losing a rotation response once or twice is plausible; a holder that keeps presenting the
        // same superseded token is storing nothing we hand back. That repetition is the signal.
        for (int i = 0; i < 3; i++) {
            assertNotNull(success(initial.refreshToken()).refreshToken(),
                "replay " + (i + 1) + " is still within tolerance");
        }

        assertRejected(initial.refreshToken(), "replaying one dead token indefinitely must be refused");
        assertTrue(refreshTokenRepository.findActiveByFamily(familyId).isEmpty(),
            "the offending lineage must be revoked");
    }

    @Test
    void refresh_repeatedReplayOnOneDeviceLeavesTheTeamsOtherDeviceUsable() {
        Team team = anyTeam();
        String subject = team.getId().toString();
        TokenResult deviceA = authService.issueTeamTokens(team, UUID.randomUUID());
        TokenResult deviceB = authService.issueTeamTokens(team, UUID.randomUUID());

        rotate(deviceA.refreshToken());
        for (int i = 0; i < 3; i++) {
            success(deviceA.refreshToken());
        }
        assertRejected(deviceA.refreshToken(), "device A trips the tripwire");

        // The whole point of scoping revocation to a lineage: device B belongs to the same team,
        // and its crew is still on the water.
        assertNotNull(rotate(deviceB.refreshToken()),
            "one device's reuse must not log out the team's other devices");
        assertFalse(refreshTokenRepository.findBySubject(subject).isEmpty(),
            "the team must keep its remaining refresh tokens");
    }

    @Test
    void refresh_expiredTokenIsRejected() {
        Team team = anyTeam();
        TokenResult initial = authService.issueTeamTokens(team);

        RefreshToken row = refreshTokenRepository.findByToken(initial.refreshToken()).orElseThrow();
        row.setExpiresAt(Instant.now().minusSeconds(60));
        refreshTokenRepository.save(row);

        assertRejected(initial.refreshToken(), "an expired refresh token must be refused");
    }

    @Test
    void refresh_expiredChainHeadIsRejectedWithoutRevoking() {
        Team team = anyTeam();
        String subject = team.getId().toString();
        TokenResult initial = authService.issueTeamTokens(team);

        String successor = rotate(initial.refreshToken());

        RefreshToken head = refreshTokenRepository.findByToken(successor).orElseThrow();
        head.setExpiresAt(Instant.now().minusSeconds(60));
        refreshTokenRepository.save(head);

        assertRejected(initial.refreshToken(), "a lineage whose head has expired must be refused");
        assertFalse(refreshTokenRepository.findBySubject(subject).isEmpty(),
            "expiry is not reuse — nothing else may be revoked");
    }

    @Test
    void refresh_unknownTokenIsRejected() {
        assertRejected(authService.generateRefreshTokenValue(), "an unknown token must be refused");
    }

    @Test
    void refresh_activeAdminRotatesNormally() {
        Admin admin = newAdmin(true);
        String refreshToken = issueAdminRefreshToken(admin);

        assertNotNull(rotate(refreshToken), "an active admin must be able to rotate its refresh token");
    }

    @Test
    void refresh_deactivatedAdminIsRefused() {
        // Deactivating an admin used to leave existing sessions untouched: rotation kept
        // minting fresh access tokens, so the account carried on working indefinitely.
        Admin admin = newAdmin(true);
        String refreshToken = issueAdminRefreshToken(admin);

        admin.setActive(false);
        adminRepository.save(admin);

        assertRejected(refreshToken, "a deactivated admin must not rotate into a fresh access token");
    }
}
