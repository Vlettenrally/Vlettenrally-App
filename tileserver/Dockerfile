# =============================================================================
# Tile Server Docker Image
# =============================================================================
# Lightweight Martin tile server that reads PMTiles from Storj S3.
# Tiles are generated separately by the tile generation pipeline.
# Config is mounted at runtime via Kubernetes ConfigMap.
# =============================================================================

FROM ghcr.io/maplibre/martin:1.16.0


EXPOSE 3000

ENTRYPOINT ["martin", "--config", "/config.yaml"]

