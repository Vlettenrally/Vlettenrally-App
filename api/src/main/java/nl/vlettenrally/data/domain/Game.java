package nl.vlettenrally.data.domain;

import lombok.Getter;
import lombok.Setter;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

/**
 * Game entity representing a rally event
 */
@Setter
@Getter
public class Game {
    // Getters and Setters
    private UUID id;
    private String name;
    private String description;
    private String participantInfo;
    private Instant startTime;
    private Instant endTime;
    private Instant checkInStartTime;
    private GameStatus status;
    private Instant createdAt;
    private Instant updatedAt;

    // External event integration
    // External event integration
    private Integer externalEventId;
    private String externalEventName;

    // Location tracking configuration
    /** Legendstracking event base URL (e.g. https://host/api/event/3265). No trailing slash, no suffix. */
    private String locationEndpointUrl;
    private Integer locationPollingFrequency;
    private Boolean locationEndpointActive;
    /** Minutes before {@link #startTime} to begin polling/ingesting locations. 0 = off. */
    private Integer locationPollingLeadMinutes;
    /**
     * Minutes after {@link #endTime} to keep polling/ingesting locations once the game is
     * COMPLETED. Keeps checkpoint-pass detection alive briefly after the end so late-finish
     * automations (a CHECKPOINT_ENTRY rule firing on a pass AFTER the game end) can still fire.
     * 0 = off.
     */
    private Integer locationPollingTailMinutes;

    /**
     * Flat points deducted once from a team that is marked finished after {@link #endTime}.
     * Applied by {@code LateFinishPenaltyService} on both finish paths (the FINISH_TEAM
     * automation action and the admin override). 0 = no penalty.
     */
    private Integer lateFinishPenaltyPoints;

    /**
     * Flat points awarded once to a team that finishes within the closing stretch of the game:
     * at or after ({@link #endTime} minus {@link #earlyFinishBonusMinutes}) and at or before
     * {@link #endTime}. Finishing earlier than that window earns nothing — this rewards a
     * strong finish, not simply racing through as fast as possible. Applied by
     * {@code EarlyFinishBonusService} on both finish paths (the FINISH_TEAM automation action
     * and the admin override). 0 = no bonus.
     */
    private Integer earlyFinishBonusPoints;

    /** Width, in minutes before {@link #endTime}, of the {@link #earlyFinishBonusPoints} window. */
    private Integer earlyFinishBonusMinutes;

    /** Whether the final ranking has been revealed to players (admin-triggered at the ceremony). */
    private Boolean rankingsPublished;

    /** Whether the ranking is visible on the public scoreboard API (independent of player-app visibility). */
    private Boolean publicRankingsEnabled;

    // Per-game location-ingest guard overrides. Null = inherit the global default from
    // application.properties (vlettenrally.location.*); a non-null value overrides that
    // guard for this game only. See LocationGuardConfig for resolution + caching.
    /** Hard teleport ceiling (km/h): any implied speed above this is dropped. Null = global. */
    private Double locMaxSpeedKmh;
    /** Soft ceiling (km/h) for the direction-gated spike rule. Null = global. */
    private Double locBoatMaxSpeedKmh;
    /** A turn sharper than this (degrees) is a spike candidate. Null = global. */
    private Double locTurnAngleThresholdDegrees;
    /** Legs shorter than this (meters) skip the turn test. Null = global. */
    private Double locMinBearingSegmentMeters;
    /** Drop fixes whose reported accuracy exceeds this (meters); null bypasses. Null = global. */
    private Double locMaxAccuracyMeters;
    /** Gaps longer than this (seconds) bypass the boat-speed/turn rules. Null = global. */
    private Long locStaleFixSeconds;

    public enum GameStatus {
        PLANNED,
        ACTIVE,
        /**
         * Emergency stop. Location ingest keeps running (tracking a capsized boat is
         * exactly when it matters most) but the competition is frozen: no distance
         * points, no checkpoint passes, no scheduled automations. Reached and left only
         * via the pause/resume endpoints, which also record the {@link GamePausePeriod}
         * that makes the frozen stretch reproducible for a later rebuild.
         */
        PAUSED,
        COMPLETED
    }

    public Game() {
        this.id = UUID.randomUUID();
        this.status = GameStatus.PLANNED;
        this.locationPollingFrequency = 30; // Matches Legendstracking 30 s cache
        this.locationEndpointActive = false;
        this.locationPollingLeadMinutes = 120; // Start ingesting 2h before the start time
        this.locationPollingTailMinutes = 120; // Keep ingesting 2h after the end (late-finish automations)
        this.lateFinishPenaltyPoints = 0; // No late-finish penalty unless configured per game
        this.earlyFinishBonusPoints = 50;
        this.earlyFinishBonusMinutes = 60;

        this.rankingsPublished = false;
        this.publicRankingsEnabled = false;
        this.createdAt = Instant.now();
        this.updatedAt = Instant.now();
    }

    public Game(UUID id, String name, String description, Instant startTime, Instant endTime, GameStatus status) {
        this();
        this.id = id;
        this.name = name;
        this.description = description;
        this.startTime = startTime;
        this.endTime = endTime;
        this.status = status;
    }

    /**
     * Whether {@code now} falls in the pre-start polling lead window: within
     * {@link #locationPollingLeadMinutes} before {@link #startTime} and before the start
     * itself. Window {@code [start - lead, start)}. A lead of 0/null (or no start time) disables it.
     */
    public boolean isWithinPollingLeadWindow(Instant now) {
        Integer lead = locationPollingLeadMinutes;
        if (lead == null || lead <= 0 || startTime == null) {
            return false;
        }
        return now.isBefore(startTime)
            && !now.isBefore(startTime.minus(Duration.ofMinutes(lead)));
    }

    /**
     * Whether {@code now} falls in the post-end polling tail window: after {@link #endTime} and
     * within {@link #locationPollingTailMinutes} of it. Window {@code (endTime, endTime + tail]}.
     * A tail of 0/null (or no end time) disables it.
     */
    public boolean isWithinPollingTailWindow(Instant now) {
        Integer tail = locationPollingTailMinutes;
        if (tail == null || tail <= 0 || endTime == null) {
            return false;
        }
        return now.isAfter(endTime)
            && !now.isAfter(endTime.plus(Duration.ofMinutes(tail)));
    }

    /**
     * The instant at which a team's digital check-in window closes: whichever is later of
     * {@code windowMinutes} after the team's own physical check-in and {@code windowMinutes}
     * after this game's planned {@link #startTime}. A team that checks in well before the game
     * starts still gets the post-start grace period; a team that checks in late still keeps its
     * own full window. Falls back to the check-in-relative deadline alone when the game has no
     * planned start time.
     */
    public Instant checkInWindowClosesAt(Instant checkedInAt, int windowMinutes) {
        Instant checkedInDeadline = checkedInAt.plus(Duration.ofMinutes(windowMinutes));
        if (startTime == null) {
            return checkedInDeadline;
        }
        Instant startDeadline = startTime.plus(Duration.ofMinutes(windowMinutes));
        return checkedInDeadline.isAfter(startDeadline) ? checkedInDeadline : startDeadline;
    }

}
