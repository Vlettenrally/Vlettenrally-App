import ws from "k6/ws";
import { check, sleep } from "k6";
import { Counter, Rate } from "k6/metrics";
import { registerDevice, wsToken } from "./lib/auth.js";
import { pollDashboard, pollCheckpoints, uploadPicture } from "./lib/traffic.js";

// --- Config (all overridable via env) -------------------------------------------------------
const scenario = JSON.parse(open(__ENV.SCENARIO || "../scenario/scenario.json"));
const teams = scenario.teams;
const gameId = scenario.gameId;

const BASE = (__ENV.BASE_URL || "https://api.vlettenrally.nl").replace(/\/$/, "");
const WS_BASE = (__ENV.WS_URL || BASE.replace(/^http/, "ws")).replace(/\/$/, "");
const DEVICES_PER_TEAM = Number(__ENV.DEVICES_PER_TEAM || 3);
const SESSION_SECONDS = Number(__ENV.SESSION_SECONDS || 120);
const PING_MS = Number(__ENV.PING_MS || 30000);
const POLL_MS = Number(__ENV.POLL_MS || 25000);
const PICTURE_RATE = Number(__ENV.PICTURE_RATE || 0.05); // fraction of sessions that upload a photo
const TARGET_VUS = Number(__ENV.TARGET_VUS || teams.length * DEVICES_PER_TEAM);
const RAMP = __ENV.RAMP || "2m";
const HOLD = __ENV.HOLD || "10m";

// --- Metrics ---------------------------------------------------------------------------------
const wsConnFail = new Rate("lt_ws_conn_fail");
const wsBroadcasts = new Counter("lt_ws_broadcasts");

// A tiny valid-ish JPEG (magic bytes + padding) so the confirm step's type/size checks pass.
const pictureBytes = (() => {
  const size = 64 * 1024;
  const buf = new Uint8Array(size);
  buf[0] = 0xff; buf[1] = 0xd8; buf[2] = 0xff; // SOI + marker
  buf[size - 2] = 0xff; buf[size - 1] = 0xd9;  // EOI
  return buf.buffer;
})();

export const options = {
  scenarios: {
    apps: {
      executor: "ramping-vus",
      startVUs: 0,
      stages: [
        { duration: RAMP, target: TARGET_VUS },
        { duration: HOLD, target: TARGET_VUS },
        { duration: "30s", target: 0 },
      ],
      gracefulStop: "30s",
    },
  },
  thresholds: {
    "http_req_duration{name:team-me}": ["p(95)<800"],
    "http_req_duration{name:achievements}": ["p(95)<800"],
    "http_req_duration{name:rankings}": ["p(95)<1000"],
    "http_req_duration{name:register-device}": ["p(95)<1500"],
    lt_register_fail: ["rate<0.02"],
    lt_ws_conn_fail: ["rate<0.05"],
    checks: ["rate>0.95"],
  },
};

// Per-VU session state (each VU is an isolated JS runtime, so module scope is per-VU).
let session = null;
let team = null;

function ensureRegistered() {
  if (session) return true;
  team = teams[(__VU - 1) % teams.length];
  session = registerDevice(BASE, gameId, team.id, `lt-vu${__VU}`);
  return session !== null;
}

export default function () {
  if (!ensureRegistered()) {
    sleep(5);
    return;
  }

  const token = wsToken(BASE, session.accessToken, gameId, team.id);
  if (!token) {
    sleep(5);
    return;
  }

  const willUpload = Math.random() < PICTURE_RATE;

  const res = ws.connect(`${WS_BASE}/ws/team/${token}`, {}, (socket) => {
    socket.on("open", () => {
      // First dashboard load on connect, like an app resuming.
      pollDashboard(BASE, session, gameId, team.id);
      pollCheckpoints(BASE, session, gameId);
    });

    socket.on("message", (msg) => {
      let type = "";
      try { type = JSON.parse(msg).type || ""; } catch (_) { /* non-JSON frame */ }
      if (type && type !== "PONG") wsBroadcasts.add(1);
    });

    socket.setInterval(() => socket.send(JSON.stringify({ type: "PING" })), PING_MS);
    socket.setInterval(() => pollDashboard(BASE, session, gameId, team.id), POLL_MS);

    if (willUpload) {
      socket.setTimeout(() => uploadPicture(BASE, session, team.id, pictureBytes), 5000);
    }

    socket.setTimeout(() => socket.close(), SESSION_SECONDS * 1000);
    socket.on("error", (e) => { if (e.error() !== "websocket: close sent") socket.close(); });
  });

  wsConnFail.add(!check(res, { "ws connected (101)": (r) => r && r.status === 101 }));
}
