#!/usr/bin/env node
// Seed a disposable load-test game and write scenario.json.
//
//   API_URL=https://api.vlettenrally.nl \
//   ADMIN_USERNAME=admin ADMIN_PASSWORD=... \
//   TEAM_COUNT=250 CHECKPOINT_COUNT=12 \
//   LOCATION_ENDPOINT_URL=http://loadtest-location:8080/api/event/1 \
//   node scripts/seed.mjs
//
// The scenario is written to loadtest/scenario/scenario.json for the mock server and k6 driver.

import { writeFileSync, mkdirSync } from "node:fs";
import { dirname, resolve } from "node:path";
import { fileURLToPath } from "node:url";

const here = dirname(fileURLToPath(import.meta.url));
const scenarioPath = resolve(here, "../scenario/scenario.json");

const API_URL = (process.env.API_URL || "https://game.vlettenrally.nl").replace(/\/$/, "");
const username = process.env.ADMIN_USERNAME || "admin";
const password = process.env.ADMIN_PASSWORD;
if (!password) {
  console.error("ADMIN_PASSWORD is required");
  process.exit(1);
}

const body = {
  teamCount: numEnv("TEAM_COUNT"),
  checkpointCount: numEnv("CHECKPOINT_COUNT"),
  centerLat: numEnv("CENTER_LAT"),
  centerLng: numEnv("CENTER_LNG"),
  spreadKm: numEnv("SPREAD_KM"),
  locationEndpointUrl: process.env.LOCATION_ENDPOINT_URL || undefined,
  seedAutomations: boolEnv("SEED_AUTOMATIONS"),
  startInMinutes: numEnv("START_IN_MINUTES"),
};

function numEnv(name) {
  const raw = process.env[name];
  if (raw == null || raw === "") return undefined;
  const n = Number(raw);
  if (!Number.isFinite(n)) throw new Error(`${name} must be a number`);
  return n;
}

function boolEnv(name) {
  const raw = process.env[name];
  if (raw == null || raw === "") return undefined;
  return raw !== "false" && raw !== "0";
}

async function main() {
  const login = await fetch(`${API_URL}/api/v1/auth/admin/login`, {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify({ username, password }),
  });
  if (!login.ok) throw new Error(`admin login failed: HTTP ${login.status} ${await login.text()}`);
  const { accessToken } = await login.json();

  const seed = await fetch(`${API_URL}/api/v1/admin/load-test/seed`, {
    method: "POST",
    headers: { "Content-Type": "application/json", Authorization: `Bearer ${accessToken}` },
    body: JSON.stringify(body),
  });
  if (!seed.ok) throw new Error(`seed failed: HTTP ${seed.status} ${await seed.text()}`);
  const scenario = await seed.json();

  mkdirSync(dirname(scenarioPath), { recursive: true });
  writeFileSync(scenarioPath, JSON.stringify(scenario, null, 2));

  console.log(`Seeded "${scenario.gameName}"`);
  console.log(`  gameId:      ${scenario.gameId}`);
  console.log(`  teams:       ${scenario.teams.length}`);
  console.log(`  checkpoints: ${scenario.checkpoints.length}`);
  console.log(`  endpoint:    ${scenario.locationEndpointUrl}`);
  console.log(`  scenario ->  ${scenarioPath}`);
  console.log(`\nTeardown with:  GAME_ID=${scenario.gameId} node scripts/teardown.mjs`);
}

main().catch((err) => {
  console.error(err.message || err);
  process.exit(1);
});
