/**
 * Shared map style resolution for MapLibre GL maps.
 *
 * Uses the remote style URL from config when available, otherwise loads the
 * local /map-style.json and patches the tile URL.
 */
import { config } from '../config';
import type { StyleSpecification } from 'maplibre-gl';

export function resolveMapStyle(): Promise<string | StyleSpecification> {
  if (config.mapStyleUrl) {
    return Promise.resolve(config.mapStyleUrl);
  }

  return fetch('/map-style.json')
    .then((res) => res.json())
    .then((style: StyleSpecification & { sources: Record<string, { tiles?: string[] }> }) => {
      // Resolve to absolute URL — MapLibre web workers can't handle relative URLs
      const tileUrl = config.tileUrl.startsWith('http')
        ? config.tileUrl
        : `${window.location.origin}${config.tileUrl}`;
      if (style.sources?.openmaptiles) {
        style.sources.openmaptiles.tiles = [tileUrl];
      }
      return style;
    });
}

/** Default map center: Netherlands */
export const DEFAULT_CENTER: [number, number] = [5.2913, 52.1326];
export const DEFAULT_ZOOM = 7;

