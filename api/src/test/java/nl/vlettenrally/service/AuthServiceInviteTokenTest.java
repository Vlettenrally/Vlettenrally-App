package nl.vlettenrally.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;

import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.TestProfile;
import jakarta.inject.Inject;
import nl.vlettenrally.api.security.MockApiTestProfile;
import nl.vlettenrally.data.domain.Team;
import nl.vlettenrally.data.dto.auth.TeamInvite;
import nl.vlettenrally.data.dto.auth.TeamInviteLinkResponse;
import nl.vlettenrally.data.dto.auth.TokenResult;
import nl.vlettenrally.data.repository.TeamRepository;

/**
 * Round-trip test for the team invite token: minting with the real signing key and
 * validating it back through {@link AuthService#validateTeamInviteToken}. Boots the full
 * stack on mock repositories (no Docker) so the genuine SmallRye JWT sign/parse path runs.
 */
@QuarkusTest
@TestProfile(MockApiTestProfile.class)
class AuthServiceInviteTokenTest {

    @Inject
    AuthService authService;

    @Inject
    TeamRepository teamRepository;

    private Team anyTeam() {
        List<Team> teams = teamRepository.findAll();
        assertFalse(teams.isEmpty(), "mock profile should provide prefilled teams");
        return teams.getFirst();
    }

    @Test
    void inviteToken_roundTrips() {
        Team team = anyTeam();

        TeamInviteLinkResponse link = authService.generateTeamInviteToken(team.getId());
        assertNotNull(link.token());
        assertTrue(link.url().contains("/check-in?invite="), "url should carry the invite token");

        Optional<TeamInvite> decoded = authService.validateTeamInviteToken(link.token());
        assertTrue(decoded.isPresent(), "freshly minted token must validate");
        assertEquals(team.getId(), decoded.get().teamId());
        assertEquals(team.getGameId(), decoded.get().gameId());
    }

    @Test
    void validate_rejectsGarbageAndNull() {
        assertTrue(authService.validateTeamInviteToken("not-a-jwt").isEmpty());
        assertTrue(authService.validateTeamInviteToken(null).isEmpty());
        assertTrue(authService.validateTeamInviteToken("  ").isEmpty());
    }

    @Test
    void validate_rejectsPlainAccessToken() {
        // A normal team access token is signed the same way but lacks the
        // purpose=team-invite claim, so it must not be accepted as an invite.
        Team team = anyTeam();
        TokenResult tokens = authService.issueTeamTokens(team);
        assertTrue(authService.validateTeamInviteToken(tokens.accessToken()).isEmpty());
    }
}
