package nl.vlettenrally.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;

import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.TestProfile;
import jakarta.inject.Inject;
import nl.vlettenrally.api.security.MockApiTestProfile;
import nl.vlettenrally.data.domain.RefreshToken;
import nl.vlettenrally.data.domain.Team;
import nl.vlettenrally.data.dto.auth.TokenResult;
import nl.vlettenrally.data.repository.RefreshTokenRepository;
import nl.vlettenrally.data.repository.TeamRepository;

/**
 * Refresh-token rotation behaviour: normal rotation, idempotent replay of a lost-response
 * retry (must NOT lock the team out), and genuine stale reuse (must still be rejected).
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
    RefreshTokenRepository refreshTokenRepository;

    private Team anyTeam() {
        List<Team> teams = teamRepository.findAll();
        assertFalse(teams.isEmpty(), "mock profile should provide prefilled teams");
        return teams.getFirst();
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
}
