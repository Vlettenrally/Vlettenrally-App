import { readFileSync } from "node:fs";
import type { LatLng } from "./geo.js";

/** Shape of scenario.json as emitted by the seed endpoint (LoadTestScenarioResponse). */
export interface Scenario {
  gameId: string;
  gameName: string;
  locationEndpointUrl: string;
  startTime: string;
  checkpoints: Array<{ name: string; lat: number; lng: number }>;
  teams: Array<{ id: string; name: string; trackerId: string }>;
}

export interface Waypoint extends LatLng {
  name: string;
}

export function loadScenario(path: string): Scenario {
  const raw = readFileSync(path, "utf8");
  const scenario = JSON.parse(raw) as Scenario;
  if (!scenario.checkpoints?.length || scenario.checkpoints.length < 2) {
    throw new Error(`Scenario ${path} needs at least 2 checkpoints as movement waypoints`);
  }
  if (!scenario.teams?.length) {
    throw new Error(`Scenario ${path} has no teams`);
  }
  for (const team of scenario.teams) {
    if (!/^\d+$/.test(team.trackerId)) {
      throw new Error(
        `Team ${team.name} has non-numeric trackerId "${team.trackerId}"; the location protocol carries tracker_id as an integer`,
      );
    }
  }
  return scenario;
}

export function waypointsOf(scenario: Scenario): Waypoint[] {
  return scenario.checkpoints.map((c) => ({ name: c.name, lat: c.lat, lng: c.lng }));
}
