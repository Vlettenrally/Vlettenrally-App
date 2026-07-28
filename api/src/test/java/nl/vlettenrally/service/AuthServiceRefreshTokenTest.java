package nl.vlettenrally.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.TestProfile;
import jakarta.inject.Inject;
import nl.vlettenrally.api.security.MockApiTestProfile;
import nl.vlettenrally.data.domain.Admin;
import nl.vlettenrally.data.domain.RefreshToken;
import nl.vlettenrally.data.domain.Team;
import nl.vlettenrally.data.dto.auth.TokenResult;
import nl.vlettenrally.data.enums.Role;
import nl.vlettenrally.data.repository.AdminRepository;
import nl.vlettenrally.data.repository.RefreshTokenRepository;
import nl.vlettenrally.data.repository.TeamRepository;

/**
 * Refresh-token rotation behaviour: normal rotation, idempotent replay of a lost-response
 * retry (must NOT lock the team out), genuine stale reuse (must still be rejected), and
 * refusal to rotate for a deactivated admin.
 * Boots on mock repositories (no Docker) so the real rotation logic runs.
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

    @Test
    void refresh_rotatesToNewPairAndRevokesOld() {
        Team team = anyTeam();
        TokenResult initial = authService.issueTeamTokens(team);

        Optional<TokenResult> refreshed = authService.refreshToken(initial.refreshToken());

        assertTrue(refreshed.isPresent(), "a live refresh token must rotate");
        assertNotNull(refreshed.get().accessToken());
        assertNotEquals(initial.refreshToken(), refreshed.get().refreshToken(),
            "rotation must issue a new refresh token");
        assertTrue(refreshTokenRepository.findByToken(initial.refreshToken()).orElseThrow().isRevoked(),
            "the old refresh token must be revoked after rotation");
    }

    @Test
    void refresh_lostResponseReplayReturnsLiveSuccessor() {
        Team team = anyTeam();
        TokenResult initial = authService.issueTeamTokens(team);

        Optional<TokenResult> first = authService.refreshToken(initial.refreshToken());
        assertTrue(first.isPresent());
        String successor = first.get().refreshToken();

        // The client never received `first` (dropped on a flaky network) and retries the old
        // token. It must get the SAME live successor back — no lockout, no family revocation.
        Optional<TokenResult> replay = authService.refreshToken(initial.refreshToken());
        assertTrue(replay.isPresent(), "idempotent replay of a lost-response retry must succeed");
        assertEquals(successor, replay.get().refreshToken(),
            "replay must return the existing live successor, not a new chain");
        assertNotNull(replay.get().accessToken(), "replay must still mint a usable access token");

        // The successor is genuinely live and can itself be rotated.
        assertTrue(authService.refreshToken(successor).isPresent(),
            "the successor token must remain usable");
    }

    @Test
    void refresh_staleReuseBeyondWindowRevokesFamily() {
        Team team = anyTeam();
        String subject = team.getId().toString();
        TokenResult initial = authService.issueTeamTokens(team);

        Optional<TokenResult> first = authService.refreshToken(initial.refreshToken());
        assertTrue(first.isPresent());

        // Simulate the old token having been rotated long ago (past the replay window) with no
        // live successor left — i.e. genuine reuse of a stale token, not a lost response.
        RefreshToken old = refreshTokenRepository.findByToken(initial.refreshToken()).orElseThrow();
        old.setRevokedAt(Instant.now().minusSeconds(3600));
        refreshTokenRepository.save(old);
        refreshTokenRepository.deleteByToken(first.get().refreshToken());

        Optional<TokenResult> replay = authService.refreshToken(initial.refreshToken());

        assertTrue(replay.isEmpty(), "stale reuse of a long-revoked token must be rejected");
        assertTrue(refreshTokenRepository.findBySubject(subject).isEmpty(),
            "genuine replay must revoke the whole team's refresh tokens");
    }

    @Test
    void refresh_activeAdminRotatesNormally() {
        Admin admin = newAdmin(true);
        String refreshToken = issueAdminRefreshToken(admin);

        assertTrue(authService.refreshToken(refreshToken).isPresent(),
            "an active admin must be able to rotate its refresh token");
    }

    @Test
    void refresh_deactivatedAdminIsRefused() {
        // Deactivating an admin used to leave existing sessions untouched: rotation kept
        // minting fresh access tokens, so the account carried on working indefinitely.
        Admin admin = newAdmin(true);
        String refreshToken = issueAdminRefreshToken(admin);

        admin.setActive(false);
        adminRepository.save(admin);

        assertTrue(authService.refreshToken(refreshToken).isEmpty(),
            "a deactivated admin must not be able to rotate into a fresh access token");
    }
}
