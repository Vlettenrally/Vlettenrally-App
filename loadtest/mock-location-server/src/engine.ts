import { offsetMeters, Route, type LatLng } from "./geo.js";
import type { Scenario, Waypoint } from "./scenario.js";

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
}

export const DEFAULT_CONFIG: Omit<EngineConfig, "simStartMs"> = {
  staggerWindowSeconds: 1200,
  speedMinKmh: 6,
  speedMaxKmh: 22, // strictly below the API's 25 km/h boat guard so fixes are never dropped
  accuracyMinMeters: 5,
  accuracyMaxMeters: 12, // below the API's 50 m accuracy guard
  jitterMeters: 3,
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

interface TeamSim {
  trackerId: number;
  participantId: number;
  route: Route;
  speedMps: number;
  speedKmh: number;
  staggerSeconds: number;
  rand: () => number;
}

export interface Fix {
  trackerId: number;
  participantId: number;
  position: LatLng;
  speedKmh: number;
  accuracyMeters: number;
  moving: boolean;
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
      return {
        trackerId,
        participantId: index + 1,
        route: new Route(permutation),
        speedKmh,
        speedMps: speedKmh / 3.6,
        staggerSeconds: rand() * config.staggerWindowSeconds,
        rand,
      };
    });
  }

  get teamCount(): number {
    return this.teams.length;
  }

  /** Compute a fix for every team at wall-clock time {@code nowMs}. */
  fixesAt(nowMs: number): Fix[] {
    const elapsed = (nowMs - this.config.simStartMs) / 1000;
    return this.teams.map((team) => {
      const t = elapsed - team.staggerSeconds;
      const moving = t > 0;
      const distance = moving ? team.speedMps * t : 0;
      const base = team.route.positionAt(distance);

      // Small GPS jitter so fixes look real (well within the accuracy/speed guards).
      const angle = team.rand() * Math.PI * 2;
      const radius = team.rand() * this.config.jitterMeters;
      const position = offsetMeters(base, Math.cos(angle) * radius, Math.sin(angle) * radius);

      const accuracy =
        this.config.accuracyMinMeters +
        team.rand() * (this.config.accuracyMaxMeters - this.config.accuracyMinMeters);

      return {
        trackerId: team.trackerId,
        participantId: team.participantId,
        position,
        speedKmh: moving ? team.speedKmh : 0,
        accuracyMeters: accuracy,
        moving,
      };
    });
  }
}

export function configFromEnv(simStartMs: number): EngineConfig {
  const num = (name: string, dflt: number): number => {
    const raw = process.env[name];
    const parsed = raw != null ? Number(raw) : NaN;
    return Number.isFinite(parsed) ? parsed : dflt;
  };
  return {
    simStartMs,
    staggerWindowSeconds: num("STAGGER_WINDOW_SECONDS", DEFAULT_CONFIG.staggerWindowSeconds),
    speedMinKmh: num("SPEED_MIN_KMH", DEFAULT_CONFIG.speedMinKmh),
    speedMaxKmh: num("SPEED_MAX_KMH", DEFAULT_CONFIG.speedMaxKmh),
    accuracyMinMeters: num("GPS_ACCURACY_MIN_M", DEFAULT_CONFIG.accuracyMinMeters),
    accuracyMaxMeters: num("GPS_ACCURACY_MAX_M", DEFAULT_CONFIG.accuracyMaxMeters),
    jitterMeters: num("JITTER_METERS", DEFAULT_CONFIG.jitterMeters),
  };
}
