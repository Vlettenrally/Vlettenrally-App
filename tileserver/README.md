# Tile Server

Pre-built vector tile server for the Vlettenrally map views.

## Architecture

Multi-stage Docker build:
1. **Build stage**: Uses [Planetiler](https://github.com/onthegomap/planetiler) to download Netherlands OSM data from Geofabrik and generate an `.mbtiles` file.
2. **Runtime stage**: Serves tiles via [Martin](https://github.com/maplibre/martin) (MapLibre tile server).

## Build

```bash
docker build -t vlettenrally-tileserver .
```

**Requirements:**
- ~2 GB RAM available during build (Planetiler JVM)
- ~2 GB final image size (mostly the .mbtiles data)

## Run

```bash
docker run -p 3000:3000 vlettenrally-tileserver
```

Tiles are served at `http://localhost:3000/netherlands/{z}/{x}/{y}`.

## Rebuild Cadence

Rebuild monthly to pick up the latest OSM data from Geofabrik.

