package nl.vlettenrally.service;

import io.quarkus.logging.Log;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import nl.vlettenrally.data.domain.AutomationRule;
import nl.vlettenrally.data.domain.Game;
import nl.vlettenrally.data.domain.Team;
import nl.vlettenrally.data.domain.automation.AutomationAction;
import nl.vlettenrally.data.domain.automation.AutomationTriggerConfig;
import nl.vlettenrally.data.dto.admin.LoadTestScenarioResponse;
import nl.vlettenrally.data.dto.game.CheckpointRequest;
import nl.vlettenrally.data.enums.AutomationTriggerType;
import nl.vlettenrally.data.enums.CheckInMethod;
import nl.vlettenrally.data.enums.MessageType;
import nl.vlettenrally.data.repository.TeamRepository;

import java.time.Duration;
import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Seeds and tears down a disposable, clearly-marked load-test game so the API can be stressed
 * without touching real event data.
 * <p>
 * A seeded game is an ordinary ACTIVE game with {@code locationEndpointActive=true} pointing at
 * the mock location server, plus N teams (each with a numeric {@code trackerId}) and M
 * checkpoints (which double as the movement waypoints). Teams are created with <b>no emergency
 * phone numbers</b> so the Twilio SMS fallback can never fire during a run.
 * <p>
 * The load-test marker is the game name prefix {@link #MARKER_PREFIX}: teardown refuses any game
 * that does not carry it, so a stray gameId can never delete a real game.
 */
@ApplicationScoped
public class LoadTestSeedService {

    /** Name prefix that marks a game as a disposable load-test fixture. */
    public static final String MARKER_PREFIX = "⚠ LOAD TEST ";

    /**
     * Base for team tracker ids. The external location protocol carries {@code tracker_id} as an
     * integer, matched to {@code teams.tracker_id} via {@code String.valueOf(int)}, so trackers
     * must be numeric strings. The high base keeps them clear of any real provider tracker ids.
     */
    private static final int TRACKER_BASE = 900_001;

    private static final double CHECKPOINT_RADIUS_METERS = 75.0;
    private static final int CHECKPOINT_POINTS = 100;
    /** 1 degree of latitude in km (spherical approximation; good to ~0.5% at these scales). */
    private static final double KM_PER_DEG_LAT = 111.32;

    @Inject
    GameService gameService;

    @Inject
    TeamRepository teamRepository;

    @Inject
    CheckpointService checkpointService;

    @Inject
    PictureService pictureService;

    @Inject
    AutomationService automationService;

    @Transactional
    public LoadTestScenarioResponse seed(int teamCount, int checkpointCount,
                                         double centerLat, double centerLng, double spreadKm,
                                         String locationEndpointUrl, boolean seedAutomations,
                                         int startInMinutes) {
        Instant now = Instant.now();
        Instant startTime = now.plus(Duration.ofMinutes(startInMinutes));

        Game game = new Game();
        game.setName(MARKER_PREFIX + DateTimeFormatter.ISO_INSTANT.format(now.truncatedTo(ChronoUnit.SECONDS)));
        game.setDescription("Automated load-test fixture. Safe to delete via DELETE /api/v1/admin/load-test/{gameId}.");
        // PLANNED: GameStatusScheduler flips it to ACTIVE at startTime, exercising the
        // GAME_STATUS_CHANGE path. Location polling still runs pre-start (within the lead window),
        // so ingestion is stressed while teams mill about before the start.
        game.setStatus(Game.GameStatus.PLANNED);
        game.setCheckInStartTime(now);
        game.setStartTime(startTime);
        game.setEndTime(startTime.plus(Duration.ofHours(4)));
        game.setLocationEndpointUrl(locationEndpointUrl);
        game.setLocationEndpointActive(true);
        game.setLocationPollingLeadMinutes(30);
        game.setLocationPollingTailMinutes(0);
        game = gameService.save(game);
        UUID gameId = game.getId();

        List<LoadTestScenarioResponse.Waypoint> waypoints = new ArrayList<>(checkpointCount);
        for (int i = 0; i < checkpointCount; i++) {
            double bearing = 2 * Math.PI * i / Math.max(1, checkpointCount);
            double[] latLng = offset(centerLat, centerLng, spreadKm, bearing);
            String name = String.format("LT CP %02d", i + 1);
            checkpointService.create(gameId, new CheckpointRequest(
                name, null, null, CHECKPOINT_POINTS, latLng[0], latLng[1],
                "circle", CHECKPOINT_RADIUS_METERS, null, false, null));
            waypoints.add(new LoadTestScenarioResponse.Waypoint(name, latLng[0], latLng[1]));
        }

        List<Team> savedTeams = new ArrayList<>(teamCount);
        List<LoadTestScenarioResponse.TeamRef> teams = new ArrayList<>(teamCount);
        for (int i = 0; i < teamCount; i++) {
            String trackerId = String.valueOf(TRACKER_BASE + i);
            Team team = new Team();
            team.setGameId(gameId);
            team.setName(String.format("LT Team %04d", i + 1));
            team.setDescription("load-test");
            team.setTrackerId(trackerId);
            team.setTrackerAssignedAt(now);
            // Pre-check-in so the app token exchange (register-device) works immediately.
            team.setCheckedIn(true);
            team.setCheckedInDigitally(true);
            team.setCheckInMethod(CheckInMethod.DIGITAL);
            // No emergency phone numbers -> Twilio SMS fallback can never fire.
            team = teamRepository.save(team);
            savedTeams.add(team);
            teams.add(new LoadTestScenarioResponse.TeamRef(team.getId(), team.getName(), trackerId));
        }

        if (seedAutomations) {
            assignRivals(savedTeams);
            seedAutomationRules(gameId);
        }

        Log.infof("Seeded load-test game %s: %d teams, %d checkpoints, automations=%s, endpoint=%s",
            gameId, teamCount, checkpointCount, seedAutomations, locationEndpointUrl);

        return new LoadTestScenarioResponse(
            gameId, game.getName(), locationEndpointUrl, game.getStartTime(), waypoints, teams);
    }

    /**
     * Delete a seeded game and everything under it. DB rows (teams, checkpoints, location_data,
     * achievements, point entries, messages, ...) fall away via {@code ON DELETE CASCADE} from
     * {@code games}; the only things that do not cascade are the teams' S3 picture objects, so
     * those are purged explicitly first.
     *
     * @throws IllegalArgumentException if no game exists with the id
     * @throws IllegalStateException    if the game is not a load-test fixture (missing marker)
     */
    @Transactional
    public void teardown(UUID gameId) {
        Game game = gameService.findById(gameId)
            .orElseThrow(() -> new IllegalArgumentException("Game not found: " + gameId));
        if (game.getName() == null || !game.getName().startsWith(MARKER_PREFIX)) {
            throw new IllegalStateException("Refusing to delete non-load-test game: " + gameId);
        }

        for (Team team : teamRepository.findByGameId(gameId)) {
            pictureService.deleteAllForTeam(team.getId());
        }
        gameService.delete(gameId);

        Log.infof("Tore down load-test game %s", gameId);
    }

    /**
     * Create automation rules that continuously exercise the engine's hot paths as the seeded
     * teams move: the built-in distance rule (per-fix accrual), a checkpoint-entry rule that awards
     * points + notifies admins (drives the ledger + ranking recompute + broadcasts on every pass),
     * a checkpoint-entry rule that messages the team (drives the message delivery pipeline), and a
     * speed-alert rule (fires whenever a team's reported speed clears the threshold). All are
     * {@code fireOnce=false} with a cooldown so they re-fire throughout the run without unbounded
     * write volume.
     */
    private void seedAutomationRules(UUID gameId) {
        // Built-in continuous distance accrual (1 point / 100 m) — evaluated on every ingested fix.
        automationService.createSystemDistanceRule(gameId);

        // Any-checkpoint config: null checkpointId means "fires on a pass of any checkpoint".
        AutomationTriggerConfig anyCheckpoint =
            new AutomationTriggerConfig(null, null, null, null, null, null, null, null, null);

        createRule(gameId, "LT checkpoint points", AutomationTriggerType.CHECKPOINT_ENTRY, anyCheckpoint,
            List.of(
                new AutomationAction.AwardPoints(25, "Checkpoint pass (load test)"),
                new AutomationAction.NotifyAdmins("LT: team passed a checkpoint")),
            1);

        createRule(gameId, "LT checkpoint message", AutomationTriggerType.CHECKPOINT_ENTRY, anyCheckpoint,
            List.of(new AutomationAction.SendMessage("Checkpoint bereikt! (load test)", MessageType.GENERAL, null, null)),
            5);

        // Teams move at 6–22 km/h, so a 12 km/h threshold fires for roughly half of them.
        AutomationTriggerConfig speed =
            new AutomationTriggerConfig(null, null, null, null, null, null, null, null, null, null, 12);
        createRule(gameId, "LT speed alert", AutomationTriggerType.SPEED_ALERT, speed,
            List.of(new AutomationAction.NotifyAdmins("LT: team exceeded 12 km/h")),
            2);

        // RIVAL_OVERTAKE fires when a team's climbing total crosses above a rival's — see assignRivals.
        createRule(gameId, "LT rival overtake", AutomationTriggerType.RIVAL_OVERTAKE, null,
            List.of(new AutomationAction.SendMessage("Je hebt een rivaal ingehaald! (load test)", MessageType.RIVAL, null, null)),
            1);
    }

    /**
     * Wire each team to a couple of rivals (the next teams in a ring) so the RIVAL_OVERTAKE rule
     * has something to compare against as points climb. Persisted via {@code teamRepository.save},
     * which rewrites the team_rivals join rows.
     */
    private void assignRivals(List<Team> savedTeams) {
        int n = savedTeams.size();
        if (n < 3) return; // need at least one distinct rival
        int rivalsPerTeam = Math.min(2, n - 1);
        for (int i = 0; i < n; i++) {
            List<UUID> rivals = new ArrayList<>(rivalsPerTeam);
            for (int r = 1; r <= rivalsPerTeam; r++) {
                rivals.add(savedTeams.get((i + r) % n).getId());
            }
            Team team = savedTeams.get(i);
            team.setRivals(rivals);
            teamRepository.save(team);
        }
    }

    private void createRule(UUID gameId, String name, AutomationTriggerType triggerType,
                            AutomationTriggerConfig config, List<AutomationAction> actions,
                            int cooldownMinutes) {
        AutomationRule rule = new AutomationRule();
        rule.setName(name);
        rule.setTriggerType(triggerType);
        rule.setTriggerConfig(config);
        rule.setActions(actions);
        rule.setFireOnce(false); // re-fire throughout the run
        rule.setCooldownMinutes(cooldownMinutes);
        automationService.createRule(gameId, rule, null);
    }

    /** Point at {@code distanceKm} from ({@code lat},{@code lng}) along {@code bearingRad}. */
    private static double[] offset(double lat, double lng, double distanceKm, double bearingRad) {
        double dLat = (distanceKm / KM_PER_DEG_LAT) * Math.cos(bearingRad);
        double dLng = (distanceKm / (KM_PER_DEG_LAT * Math.cos(Math.toRadians(lat)))) * Math.sin(bearingRad);
        return new double[]{lat + dLat, lng + dLng};
    }
}
