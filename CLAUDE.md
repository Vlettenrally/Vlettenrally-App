# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Repository Overview

Vlettenrally App is a real-time web application for managing Dutch scouting boat rally events. It consists of three main components:

- `api/` — Quarkus (Java) REST + WebSocket backend
- `frontend/` — React + TypeScript + Vite PWA
- `tileserver/` — Martin tile server serving PMTiles from S3 (Netherlands map)

Deployed on Kubernetes. The API runs as a native GraalVM image in production.

## Commands

### API (run from `api/`)

```bash
# Dev mode with live reload (starts Testcontainers DB automatically)
./mvnw quarkus:dev

# Dev mode with explicit mock profile (in-memory repos, no DB needed)
./mvnw quarkus:dev -Dquarkus.profile=mock

# Run all tests
./mvnw test

# Run a single test class
./mvnw test -Dtest=GeofencingServiceTest

# Build JAR
./mvnw package

# Build native image (requires GraalVM or Docker)
./mvnw package -Dnative
./mvnw package -Dnative -Dquarkus.native.container-build=true
```

### Frontend (run from `frontend/`)

```bash
npm install
npm run dev

# Dev with mock data (no backend needed)
VITE_USE_MOCKS=true npm run dev

# Type check + build
npm run build

# Lint
npm run lint
```

## Architecture

### Authentication

Three-token system: **Registration token** (one-time, 15 min) → **Access token** (1 hr) + **Refresh token** (7 days). Teams receive a registration token via redirect from a third-party pre-registration system (WordPress), exchange it for access/refresh tokens, then use Bearer auth for all subsequent requests. See `docs/AUTHENTICATION.md` for the full flow.

Custom security annotations `@TeamScoped` and `@TeamScopedOrAdmin` (in `api/.../api/security/`) enforce that teams can only access their own data.

### Repository Pattern with Mock/JPA Switching

Every domain repository has three implementations:
- Interface in `data/repository/` (e.g., `GameRepository`)
- JPA implementation in `data/repository/jpa/` (e.g., `JpaGameRepository`)
- In-memory mock in `data/repository/mock/` (e.g., `MockGameRepository`)

Implementations are selected at build time via `@IfBuildProfile`: mocks (with prefilled data) in `dev`/`test`, JPA in `prod`. JPA repositories extend `ReadReplicaAwareRepository`: writes and reads inside an active transaction go to the primary; reads outside a transaction go to the read replica (`getReadEntityManager(true)` forces the primary).

### WebSocket Clustering via Valkey

WebSocket sessions are not stored in-process. `ValkeySessionStore` persists session metadata in Valkey (Redis-compatible), and `ValkeyPubSubService` distributes broadcasts across API instances. This means adding/removing instances is safe without sticky sessions. Two endpoints exist: `TeamWebSocket` (per-team notifications) and `AdminWebSocket` (admin dashboard events).

WebSocket connections require a short-lived token obtained from `POST /api/v1/ws/token` before upgrading — this avoids passing the access token as a query parameter.

### Message Delivery Pipeline

Messages are delivered over two channels via the `MessageTransmitter` interface:
- **WebSocket** — real-time, when team is connected
- **Firebase FCM** (`FirebasePushTransmitter`) — push notification fallback
- **Twilio SMS** (`TwilioSmsTransmitter`) — critical fallback when team has been inactive > 60 min (configurable via `vlettenrally.team.inactive-threshold-minutes`)

`MessageService` decides which channels to use based on team activity and message priority.

### Location Data

Location is polled from an external API (configured via `api_endpoint_config` table) by `LocationPollingScheduler`. Data is stored in a **TimescaleDB hypertable** (`location_data`) with PostGIS geometry on checkpoints. `GeofencingService` delegates proximity checks to PostGIS rather than computing distances in Java.

### Frontend State Management

State is managed with Zustand stores (`useGameStore`, `useTeamStore`, `useMessageStore`). Real-time updates arrive via `useWebSocket` hook → `useRealtime` processes events and updates stores. The `api.ts` service handles all REST calls with Bearer token auth; `auth.ts` manages token storage and refresh.

Frontend environment variables (all `VITE_*` prefixed):
- `VITE_USE_MOCKS` — bypass backend entirely
- `VITE_API_URL` / `VITE_WS_URL` — backend endpoints
- `VITE_MAP_STYLE_URL` — leave empty to use self-hosted tiles via tileserver
- Firebase config vars for FCM push notifications

### MUI v7 Component Props

The UI uses MUI v7, where `inputProps`, `InputProps`, and `componentsProps` are deprecated. Always use `slotProps` instead:

```tsx
// Wrong (deprecated)
<TextField InputProps={{ readOnly: true, endAdornment: <Icon /> }} inputProps={{ maxLength: 10 }} />

// Right
<TextField slotProps={{ input: { readOnly: true, endAdornment: <Icon /> }, htmlInput: { maxLength: 10 } }} />
```

`InputProps` maps to `slotProps.input` (the Input component), `inputProps` maps to `slotProps.htmlInput` (the underlying `<input>` element).

### Database

PostgreSQL 17 with TimescaleDB + PostGIS extensions. Schema managed by Flyway (migrations in `api/src/main/resources/db/migration/`). Dev mode uses Testcontainers with `timescale/timescaledb-ha:pg17` automatically. Production uses CNPG (CloudNativePG) with a read replica — the `"read-replica"` persistence unit is configured separately.

## Key Mock Data IDs

For testing against the mock profile or `VITE_USE_MOCKS=true`:
- Game: `a1b2c3d4-e5f6-7890-abcd-ef1234567890`
- Team "De Watervlooien": `11111111-1111-1111-1111-111111111111`
- Team "Windkracht Tien": `22222222-2222-2222-2222-222222222222`
- Device ID: `aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa`

## Native Image Considerations

When adding new classes that use reflection, crypto, or static initialization, they may need entries in `quarkus.native.additional-build-args` in `application.properties` (see the existing `--initialize-at-run-time` list). PostGIS and Twilio already have reflection configs registered in `PostGisReflectionConfig` and `TwilioReflectionConfig`.
