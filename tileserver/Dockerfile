# =============================================================================
# Tile Server - Multi-stage Docker build
# =============================================================================
# Stage 1: Download Netherlands OSM data and build .mbtiles with Planetiler
# Stage 2: Serve tiles with Martin (MapLibre tile server)
# =============================================================================

# Stage 1: Build .mbtiles with Planetiler
FROM eclipse-temurin:21-jdk AS builder

WORKDIR /build

# Download latest Planetiler release
RUN apt-get update && apt-get install -y wget && \
    wget -q https://github.com/onthegomap/planetiler/releases/latest/download/planetiler.jar

# Download Netherlands OSM data and generate .mbtiles
# Uses sparsearray + mmap to keep memory usage under ~4GB (fits in CI runners)
RUN java -Xmx4g -jar planetiler.jar \
    --download \
    --area=netherlands \
    --output=/build/output.mbtiles \
    --nodemap-type=sparsearray \
    --storage=mmap

# Stage 2: Serve with Martin
FROM ghcr.io/maplibre/martin:v0.15.0

COPY --from=builder /build/output.mbtiles /data/output.mbtiles
COPY config.yaml /config.yaml

EXPOSE 3000

ENTRYPOINT ["martin", "--config", "/config.yaml"]

