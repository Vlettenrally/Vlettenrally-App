import { offsetMeters, Route, type LatLng } from "./geo.js";
import type { Scenario, Waypoint } from "./scenario.js";

/** How a dark tracker shows up in /participants_locations: repeat its last fix, or vanish. */
export type OutageMode = "stale" | "omit";

export interface EngineConfig {
  /** Reference epoch (ms) that t=0 of the simulation maps to. Defaults to engine construction. */
  simStartMs: number;
  /** Teams begin moving at a random offset in [0, staggerWindowSeconds). */
  staggerWindowSeconds: number;
  /** Per-team constant boat speed drawn uniformly in [speedMinKmh, speedMaxKmh]. */
  speedMinKmh: number;
  speedMaxKmh: number;
  /** Reported GPS accuracy drawn in [accuracyMinMeters, accuracyMaxMeters]. */
  accuracyMinMeters: number;
  accuracyMaxMeters: number;
  /** Max positional jitter (metres) added to each reported fix. */
  jitterMeters: number;
  /** Fraction of teams whose tracker periodically goes dark (exercises backfill). 0 disables. */
  outageTeamFraction: number;
  /** Outages repeat every cycle; each prone team gets a fixed phase offset within it. */
  outageCycleSeconds: number;
  /** Per-team outage duration drawn in [outageMinSeconds, outageMaxSeconds]. */
  outageMinSeconds: number;
  outageMaxSeconds: number;
  outageMode: OutageMode;
  /** Sampling interval of the /participant_track history served for backfills. */
  trackSampleSeconds: number;
}

export const DEFAULT_CONFIG: Omit<EngineConfig, "simStartMs"> = {
  staggerWindowSeconds: 1200,
  speedMinKmh: 6,
  speedMaxKmh: 22, // strictly below the API's 25 km/h boat guard so fixes are never dropped
  accuracyMinMeters: 5,
  accuracyMaxMeters: 12, // below the API's 50 m accuracy guard
  jitterMeters: 3,
  outageTeamFraction: 0.25,
  // Cycle > API cooldown (PT15M) + max outage, so a recovered team is never still cooling down
  // when its next gap resolves; durations > gap-threshold (PT5M) or no backfill ever fires.
  outageCycleSeconds: 1800,
  outageMinSeconds: 390,
  outageMaxSeconds: 600,
  outageMode: "stale",
  trackSampleSeconds: 30,
};

/** Deterministic PRNG so a team's route/speed/stagger are stable across restarts. */
function mulberry32(seed: number): () => number {
  let a = seed >>> 0;
  return () => {
    a |= 0;
    a = (a + 0x6d2b79f5) | 0;
    let t = Math.imul(a ^ (a >>> 15), 1 | a);
    t = (t + Math.imul(t ^ (t >>> 7), 61 | t)) ^ t;
    return ((t ^ (t >>> 14)) >>> 0) / 4294967296;
  };
}

function shuffle<T>(items: T[], rand: () => number): T[] {
  const out = items.slice();
  for (let i = out.length - 1; i > 0; i--) {
    const j = Math.floor(rand() * (i + 1));
    [out[i], out[j]] = [out[j], out[i]];
  }
  return out;
}

/**
 * Stateless PRNG for a (team, sim-time) point, so a stale repeated fix and every
 * /participant_track sample are byte-identical across requests and server restarts.
 */
function pointRand(trackerId: number, tSeconds: number): () => number {
  return mulberry32((Math.imul(trackerId, 0x9e3779b1) ^ Math.floor(tSeconds)) >>> 0);
}

interface TeamSim {
  trackerId: number;
  participantId: number;
  route: Route;
  speedMps: number;
  speedKmh: number;
  staggerSeconds: number;
  rand: () => number;
  /** Set when this team's tracker periodically goes dark. */
  outage: { phaseSeconds: number; durationSeconds: number } | null;
}

export interface Fix {
  trackerId: number;
  participantId: number;
  position: LatLng;
  speedKmh: number;
  accuracyMeters: number;
  moving: boolean;
  /** Wall-clock time this fix was recorded at (frozen at outage start for a stale fix). */
  timestampMs: number;
}

export class MovementEngine {
  private readonly config: EngineConfig;
  private readonly teams: TeamSim[];

  constructor(scenario: Scenario, waypoints: Waypoint[], config: EngineConfig) {
    this.config = config;
    this.teams = scenario.teams.map((team, index) => {
      const trackerId = Number(team.trackerId);
      const rand = mulberry32(trackerId);
      const permutation = shuffle(waypoints, rand);
      const speedKmh =
        config.speedMinKmh + rand() * (config.speedMaxKmh - config.speedMinKmh);

      // Separate salted stream so enabling/tuning outages never shifts routes/speeds/staggers.
      const outageRand = mulberry32((trackerId ^ 0x5eed5eed) >>> 0);
      const prone = outageRand() < config.outageTeamFraction;
      const outage = prone
        ? {
            phaseSeconds: outageRand() * config.outageCycleSeconds,
            durationSeconds:
              config.outageMinSeconds +
              outageRand() * (config.outageMaxSeconds - config.outageMinSeconds),
          }
        : null;

      return {
        trackerId,
        participantId: index + 1,
        route: new Route(permutation),
        speedKmh,
        speedMps: speedKmh / 3.6,
        staggerSeconds: rand() * config.staggerWindowSeconds,
        rand,
        outage,
      };
    });
  }

  get teamCount(): number {
    return this.teams.length;
  }

  /**
   * Compute a fix for every team at wall-clock time {@code nowMs}. Teams in an outage are
   * reported per {@code outageMode}: their last pre-outage fix (frozen position AND timestamp,
   * like the real upstream keeps serving an offline tracker's last report), or omitted.
   */
  fixesAt(nowMs: number): Fix[] {
    const elapsed = (nowMs - this.config.simStartMs) / 1000;
    const fixes: Fix[] = [];
    for (const team of this.teams) {
      const outageStart = this.outageStartAt(team, elapsed);
      if (outageStart == null) {
        fixes.push(this.fixFor(team, elapsed, team.rand));
      } else if (this.config.outageMode === "stale") {
        fixes.push(this.fixFor(team, outageStart, pointRand(team.trackerId, outageStart)));
      }
      // omit mode: dark team is simply absent from the response
    }
    return fixes;
  }

  /**
   * A participant's full history from sim start to {@code nowMs}, sampled every
   * {@code trackSampleSeconds} — including movement during outages (that buffered stretch is
   * exactly what the API's backfill fetches this track to recover). Null for unknown ids.
   */
  trackFor(participantId: number, nowMs: number): Fix[] | null {
    const team = this.teams.find((t) => t.participantId === participantId);
    if (!team) return null;
    const elapsed = (nowMs - this.config.simStartMs) / 1000;
    const fixes: Fix[] = [];
    for (let t = 0; t < elapsed; t += this.config.trackSampleSeconds) {
      fixes.push(this.fixFor(team, t, pointRand(team.trackerId, t)));
    }
    if (elapsed > 0) {
      fixes.push(this.fixFor(team, elapsed, pointRand(team.trackerId, elapsed)));
    }
    return fixes;
  }

  /** Outage snapshot for /health. */
  outageStats(nowMs: number): { mode: OutageMode; proneTeams: number; darkTeams: number } {
    const elapsed = (nowMs - this.config.simStartMs) / 1000;
    let prone = 0;
    let dark = 0;
    for (const team of this.teams) {
      if (!team.outage) continue;
      prone++;
      if (this.outageStartAt(team, elapsed) != null) dark++;
    }
    return { mode: this.config.outageMode, proneTeams: prone, darkTeams: dark };
  }

  /** Sim-time (seconds) the team's current outage started, or null when it is reporting. */
  private outageStartAt(team: TeamSim, elapsedSeconds: number): number | null {
    if (!team.outage || elapsedSeconds <= 0) return null;
    const cycle = this.config.outageCycleSeconds;
    const pos =
      (((elapsedSeconds - team.outage.phaseSeconds) % cycle) + cycle) % cycle;
    if (pos >= team.outage.durationSeconds) return null;
    // An outage straddling sim start is clamped: t=0 is the earliest fix that ever existed.
    return Math.max(0, elapsedSeconds - pos);
  }

  /** Movement fix for a team at sim-time {@code elapsedSeconds}, jittered via {@code rand}. */
  private fixFor(team: TeamSim, elapsedSeconds: number, rand: () => number): Fix {
    const t = elapsedSeconds - team.staggerSeconds;
    const moving = t > 0;
    const distance = moving ? team.speedMps * t : 0;
    const base = team.route.positionAt(distance);

    // Small GPS jitter so fixes look real (well within the accuracy/speed guards).
    const angle = rand() * Math.PI * 2;
    const radius = rand() * this.config.jitterMeters;
    const position = offsetMeters(base, Math.cos(angle) * radius, Math.sin(angle) * radius);

    const accuracy =
      this.config.accuracyMinMeters +
      rand() * (this.config.accuracyMaxMeters - this.config.accuracyMinMeters);

    return {
      trackerId: team.trackerId,
      participantId: team.participantId,
      position,
      speedKmh: moving ? team.speedKmh : 0,
      accuracyMeters: accuracy,
      moving,
      timestampMs: this.config.simStartMs + elapsedSeconds * 1000,
    };
  }
}

export function configFromEnv(simStartMs: number): EngineConfig {
  const num = (name: string, dflt: number): number => {
    const raw = process.env[name];
    const parsed = raw != null ? Number(raw) : NaN;
    return Number.isFinite(parsed) ? parsed : dflt;
  };
  const outageMode = process.env.OUTAGE_MODE ?? DEFAULT_CONFIG.outageMode;
  if (outageMode !== "stale" && outageMode !== "omit") {
    throw new Error(`OUTAGE_MODE must be "stale" or "omit", got "${outageMode}"`);
  }
  return {
    simStartMs,
    staggerWindowSeconds: num("STAGGER_WINDOW_SECONDS", DEFAULT_CONFIG.staggerWindowSeconds),
    speedMinKmh: num("SPEED_MIN_KMH", DEFAULT_CONFIG.speedMinKmh),
    speedMaxKmh: num("SPEED_MAX_KMH", DEFAULT_CONFIG.speedMaxKmh),
    accuracyMinMeters: num("GPS_ACCURACY_MIN_M", DEFAULT_CONFIG.accuracyMinMeters),
    accuracyMaxMeters: num("GPS_ACCURACY_MAX_M", DEFAULT_CONFIG.accuracyMaxMeters),
    jitterMeters: num("JITTER_METERS", DEFAULT_CONFIG.jitterMeters),
    outageTeamFraction: Math.max(0, Math.min(1, num("OUTAGE_TEAM_FRACTION", DEFAULT_CONFIG.outageTeamFraction))),
    outageCycleSeconds: num("OUTAGE_CYCLE_SECONDS", DEFAULT_CONFIG.outageCycleSeconds),
    outageMinSeconds: num("OUTAGE_MIN_SECONDS", DEFAULT_CONFIG.outageMinSeconds),
    outageMaxSeconds: num("OUTAGE_MAX_SECONDS", DEFAULT_CONFIG.outageMaxSeconds),
    outageMode,
    trackSampleSeconds: num("TRACK_SAMPLE_SECONDS", DEFAULT_CONFIG.trackSampleSeconds),
  };
}
