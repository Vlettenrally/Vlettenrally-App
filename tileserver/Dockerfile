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
# --Xmx2g: Planetiler needs ~2GB RAM for Netherlands data
RUN java -Xmx2g -jar planetiler.jar \
    --download \
    --area=netherlands \
    --output=/build/output.mbtiles \
    --nodemap-type=array \
    --storage=ram

# Stage 2: Serve with Martin
FROM ghcr.io/maplibre/martin:v0.15.0

COPY --from=builder /build/output.mbtiles /data/output.mbtiles
COPY config.yaml /config.yaml

EXPOSE 3000

ENTRYPOINT ["martin", "--config", "/config.yaml"]

