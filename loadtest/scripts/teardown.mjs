#!/usr/bin/env node
// Delete a load-test game (and its S3 picture objects). All other rows cascade in the DB.
//
//   API_URL=https://api.vlettenrally.nl ADMIN_USERNAME=admin ADMIN_PASSWORD=... \
//   GAME_ID=<uuid> node scripts/teardown.mjs
//
// If GAME_ID is omitted, the gameId from loadtest/scenario/scenario.json is used.

import { readFileSync } from "node:fs";
import { dirname, resolve } from "node:path";
import { fileURLToPath } from "node:url";

const here = dirname(fileURLToPath(import.meta.url));
process.loadEnvFile?.(resolve(here, ".env")); // load scripts/.env if present; inline env still wins
const scenarioPath = resolve(here, "../scenario/scenario.json");

const API_URL = (process.env.API_URL || "https://api.vlettenrally.nl").replace(/\/$/, "");
const username = process.env.ADMIN_USERNAME || "admin";
const password = process.env.ADMIN_PASSWORD;
if (!password) {
  console.error("ADMIN_PASSWORD is required");
  process.exit(1);
}

let gameId = process.env.GAME_ID;
if (!gameId) {
  try {
    gameId = JSON.parse(readFileSync(scenarioPath, "utf8")).gameId;
  } catch {
    console.error("GAME_ID not set and no scenario.json found");
    process.exit(1);
  }
}

async function main() {
  const login = await fetch(`${API_URL}/api/v1/auth/admin/login`, {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify({ username, password }),
  });
  if (!login.ok) throw new Error(`admin login failed: HTTP ${login.status} ${await login.text()}`);
  const { accessToken } = await login.json();

  const res = await fetch(`${API_URL}/api/v1/admin/load-test/${gameId}`, {
    method: "DELETE",
    headers: { Authorization: `Bearer ${accessToken}` },
  });
  if (res.status === 204) {
    console.log(`Deleted load-test game ${gameId}`);
    return;
  }
  throw new Error(`teardown failed: HTTP ${res.status} ${await res.text()}`);
}

main().catch((err) => {
  console.error(err.message || err);
  process.exit(1);
});
