import Fastify from "fastify";
import { configFromEnv, MovementEngine } from "./engine.js";
import { loadScenario, waypointsOf } from "./scenario.js";

const PORT = Number(process.env.PORT ?? 8080);
const HOST = process.env.HOST ?? "0.0.0.0";
const SCENARIO_PATH = process.env.SCENARIO_PATH ?? "/scenario/scenario.json";
const EVENT_ID = Number(process.env.EVENT_ID ?? 1);

const scenario = loadScenario(SCENARIO_PATH);
const waypoints = waypointsOf(scenario);
const simStartMs = process.env.SIM_START_MS ? Number(process.env.SIM_START_MS) : Date.now();
const engine = new MovementEngine(scenario, waypoints, configFromEnv(simStartMs));

const app = Fastify({ logger: { level: process.env.LOG_LEVEL ?? "info" } });

function isoNow(): string {
  return new Date().toISOString();
}

/** GET {base}/details — event metadata, participants and checkpoints (EventDetailsResponse). */
app.get("/api/event/:id/details", async () => {
  const startIso = scenario.startTime ?? isoNow();
  return {
    event: {
      eventDetails: [
        {
          id: EVENT_ID,
          name: scenario.gameName,
          start_date: startIso,
          actual_start_date: startIso,
          end_date: null,
        },
      ],
      eventParticipants: scenario.teams.map((team, index) => ({
        event_participants_id: index + 1,
        event_id: EVENT_ID,
        first_name: team.name,
        last_name: "",
        country: "NL",
        gender: "X",
        bib: index + 1,
        bib_label: team.name,
        start_date: startIso,
      })),
      eventCheckpoints: scenario.checkpoints.map((cp, index) => ({
        id: index + 1,
        event_id: EVENT_ID,
        location: { x: cp.lng, y: cp.lat },
        name: cp.name,
        description: "",
        type_name: "checkpoint",
        sequence: index + 1,
        radius: 75.0,
        polygon_coordinates: null,
      })),
    },
  };
});

/** GET {base}/participants_locations — live positions (EventParticipantsLocationsResponse). */
app.get("/api/event/:id/participants_locations", async () => {
  const now = isoNow();
  const fixes = engine.fixesAt(Date.now());
  return {
    event: {
      eventParticipantLocations: fixes.map((fix) => ({
        event_participants_id: fix.participantId,
        status_id: 1,
        tracker_id: fix.trackerId,
        location_time: now,
        report_time: now,
        location: { x: fix.position.lng, y: fix.position.lat },
        battery_percentage: 100,
        is_sos_report: 0,
        gps_accuracy: Math.round(fix.accuracyMeters),
        speed: Number(fix.speedKmh.toFixed(1)),
      })),
      eventParticipantDistances: [],
    },
  };
});

app.get("/health", async () => ({
  status: "ok",
  game: scenario.gameName,
  teams: engine.teamCount,
  checkpoints: scenario.checkpoints.length,
  simStartMs,
}));

app
  .listen({ port: PORT, host: HOST })
  .then(() => {
    app.log.info(
      `mock location server up: ${engine.teamCount} teams, ${scenario.checkpoints.length} checkpoints, event ${EVENT_ID}`,
    );
  })
  .catch((err) => {
    app.log.error(err);
    process.exit(1);
  });
