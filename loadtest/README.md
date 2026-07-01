# Vlettenrally load test harness

Drives the two independent stress paths of the API at scale:

1. **Location ingestion** — a mock server impersonates the Legendstracking external API and makes
   simulated teams *move* (straight lines between checkpoints, boat speeds, random order,
   staggered starts). The real API polls it every 15s and runs the whole ingest → PostGIS
   geofencing → checkpoint detection → broadcast pipeline. This path is **leader-elected**, so it
   stresses a single API pod.
2. **App usage** — a k6 script simulates ~750 player-app devices: check-in, WebSocket keepalive,
   dashboard polling, and occasional picture upload. This spreads across all pods.

Both read one generated `scenario.json`.

```
loadtest/
  scripts/            seed.mjs / teardown.mjs  (Node ESM, no deps)
  scenario/           generated scenario.json (gitignored)
  mock-location-server/  Fastify + movement engine (+ Dockerfile, k8s/)
  k6/                 app-load.js + lib/
```

## Components

| Part | What it does |
|------|--------------|
| `POST /api/v1/admin/load-test/seed` | super_admin API endpoint. Creates a **marked** game (`⚠ LOAD TEST …`), N pre-checked-in teams (numeric `trackerId`s, no phone numbers), M checkpoints, and (by default) automation rules. Returns the scenario. |
| `DELETE /api/v1/admin/load-test/{gameId}` | Deletes a marked game + its S3 picture objects; refuses unmarked games. |
| `scripts/seed.mjs` | Logs in as admin, calls seed, writes `scenario/scenario.json`. |
| `mock-location-server` | Serves `/api/event/:id/{details,participants_locations}` with live positions. |
| `k6/app-load.js` | Ramps app VUs; register-device → ws PING → poll → picture upload. |

## Prerequisites

- Node 22+ (for the scripts and mock server; k6 provides its own runtime).
- [k6](https://k6.io/docs/get-started/installation/).
- A super_admin account on the target API.

## 1. Seed

```bash
API_URL=https://api.vlettenrally.nl \
ADMIN_USERNAME=admin ADMIN_PASSWORD='…' \
TEAM_COUNT=250 CHECKPOINT_COUNT=12 \
CENTER_LAT=52.35 CENTER_LNG=4.90 SPREAD_KM=3 \
START_IN_MINUTES=7 \
LOCATION_ENDPOINT_URL=http://loadtest-location:8080/api/event/1 \
node scripts/seed.mjs
```

Writes `scenario/scenario.json`. The game is created **PLANNED** and auto-transitions to **ACTIVE**
`START_IN_MINUTES` from now (default 7, meant to be 5–10). Check-in is open immediately and location
polling runs during the pre-start window, so bring the mock server up promptly (until then the
poller just logs a fetch error each tick). **Scoring and the automation engine only kick in once the
game activates** — set the k6 `HOLD` long enough to cover the pre-start window plus the active run.

**Automation rules** are seeded by default so the automation engine is load-tested as teams move:

| Rule | Trigger | Action | Exercises |
|------|---------|--------|-----------|
| Afstandspunten (system) | distance travelled | 1 pt / 100 m | per-fix accrual on the leader |
| LT checkpoint points | any checkpoint pass | award points + notify admins | ledger write → ranking recompute → broadcast |
| LT checkpoint message | any checkpoint pass | message the team | message delivery pipeline (WS) |
| LT speed alert | speed ≥ 12 km/h | notify admins | speed-alert eval + admin WS |
| LT rival overtake | team's total passes a rival's | message the team | rival-comparison path (teams are wired into a rivals ring) |

All are re-firing (cooldown 1–5 min) so they keep the engine busy without unbounded writes. Set
`SEED_AUTOMATIONS=false` on the seed command to skip them and isolate the rest of the harness.

## 2. Mock location server

The API polls this, so it must be reachable from the API pods. Two options:

**In-cluster (recommended for prod):**
```bash
# build & push
docker build -t ghcr.io/joostvanviegen/vlettenrally-loadtest-location:latest mock-location-server
docker push ghcr.io/joostvanviegen/vlettenrally-loadtest-location:latest

# scenario as a ConfigMap, then deploy
kubectl create configmap loadtest-scenario --from-file=scenario.json=scenario/scenario.json
kubectl apply -f mock-location-server/k8s/service.yaml
kubectl apply -f mock-location-server/k8s/deployment.yaml
```
Seed with `LOCATION_ENDPOINT_URL=http://loadtest-location.<namespace>.svc:8080/api/event/1`.

**Local (for validation against `quarkus:dev`):**
```bash
cd mock-location-server && npm install
SCENARIO_PATH=../scenario/scenario.json PORT=8080 npm run dev
```
Seed with `LOCATION_ENDPOINT_URL=http://localhost:8080/api/event/1` (or `host.docker.internal`).

Movement knobs (env): `SPEED_MIN_KMH`/`SPEED_MAX_KMH` (default 6–22, kept **below the API's 25 km/h
guard**), `STAGGER_WINDOW_SECONDS` (default 1200), `GPS_ACCURACY_MIN_M`/`MAX_M`, `JITTER_METERS`.

## 3. k6 app load

```bash
BASE_URL=https://api.vlettenrally.nl \
WS_URL=wss://api.vlettenrally.nl \
SCENARIO=scenario/scenario.json \
DEVICES_PER_TEAM=3 SESSION_SECONDS=120 \
RAMP=2m HOLD=10m \
k6 run k6/app-load.js
```

Smoke test first (small, short):
```bash
BASE_URL=… WS_URL=… TARGET_VUS=10 RAMP=10s HOLD=40s SESSION_SECONDS=30 k6 run k6/app-load.js
```

Stream to Grafana (optional, sits alongside `kubernetes/alloy.yaml` + `otel.yaml`):
```bash
K6_PROMETHEUS_RW_SERVER_URL=http://…/api/v1/write k6 run -o experimental-prometheus-rw k6/app-load.js
```

### Running k6 in-cluster (Job)

To generate load from inside the cluster (avoids the public ingress and any operator-machine
bottleneck), build the k6 image and apply the Job:
```bash
docker build -t ghcr.io/joostvanviegen/vlettenrally-loadtest-k6:latest k6
docker push ghcr.io/joostvanviegen/vlettenrally-loadtest-k6:latest

# Reuses the loadtest-scenario ConfigMap created for the mock server.
kubectl apply -f k6/k8s/job.yaml
kubectl logs -f job/loadtest-k6
```
The Job defaults to the in-cluster API service (`vlettenrally-api:8080`), `DEVICES_PER_TEAM=3`,
`RAMP=2m`, `HOLD=10m`. Edit `k6/k8s/job.yaml` env to change the profile or enable Prometheus
output. It never retries (`backoffLimit: 0`) and self-deletes 1h after finishing.

## 4. Teardown (always)

```bash
API_URL=… ADMIN_USERNAME=admin ADMIN_PASSWORD='…' node scripts/teardown.mjs   # uses scenario.json's gameId
kubectl delete -f mock-location-server/k8s/ ; kubectl delete configmap loadtest-scenario
```

## ⚠ Production safety

- **Run only when no real event is ACTIVE/PLANNED** — the poller processes every eligible game.
- Seeded teams have **no emergency phone numbers** and the run keeps them active (continuous
  polling), so the Twilio SMS fallback (60-min inactivity) never fires. Do **not** send
  `POST /messages/admin` to these teams unless you specifically want to test FCM.
- The load-test game is name-marked; teardown refuses anything unmarked.
- Watch the **leader pod** first (single-instance location ingest: 250 fixes × M checkpoints of
  PostGIS geofencing + Valkey fan-out every 15s), then HPA replica count (min 1 / **max 5**), DB
  (CNPG), and Valkey. Record where p95 latency and error rates break.
