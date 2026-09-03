import { describe, it, expect } from 'vitest';

/**
 * The rest of the suite mocks MapView out wholesale, so nothing else here ever
 * imports maplibre-gl for real — the v5 -> v6 bump broke every map in the app
 * while all 185 tests stayed green.
 *
 * Scope, so this is not mistaken for more than it is: the *export shape* of
 * maplibre-gl is guarded by `tsc` (it is what caught the v6 break, TS1192), not
 * by this file — Vite's transform interops a wrong default import, so a test
 * cannot see it. What these cases do cover is the runtime side `tsc` can't type:
 * that both map modules still evaluate under a fresh maplibre, and that the
 * named exports the components and the worker setup reach for still exist.
 *
 * Nothing touches WebGL until `new maplibregl.Map()` runs, so this is safe
 * under jsdom.
 */
describe('map modules', () => {
  it('maplibre-gl exposes the named exports the map components use', async () => {
    const maplibregl = await import('maplibre-gl');

    for (const name of [
      'Map',
      'Marker',
      'Popup',
      'LngLatBounds',
      'NavigationControl',
      'GeolocateControl',
      'setWorkerUrl',
    ] as const) {
      expect(maplibregl[name], `maplibre-gl no longer exports ${name}`).toBeTypeOf('function');
    }
  });

  it('imports MapView without throwing', async () => {
    await expect(import('./MapView')).resolves.toBeDefined();
  });

  it('imports LocationPickerMap without throwing', async () => {
    await expect(import('./LocationPickerMap')).resolves.toBeDefined();
  });
}, 10_000);
