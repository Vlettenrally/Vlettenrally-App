import { describe, expect, it } from 'vitest';
import { toMarkers } from './publicMapMarkers';
import { STALE_AFTER_MS, TEAM_COLOR } from '../admin/mapConstants';
import type { PublicMapResponse } from '../../types/entities';

/**
 * Tests for the public map's payload → marker mapping. The guarantee that matters is that nothing
 * on a public display can ever be flagged `hidden`: the server strips hidden zones and does not send
 * the field, and this mapping must not reintroduce it.
 */
describe('toMarkers', () => {
  const NOW = Date.parse('2026-04-01T12:00:00Z');

  const payload = (overrides: Partial<PublicMapResponse> = {}): PublicMapResponse => ({
    gameId: 'a1b2c3d4-e5f6-7890-abcd-ef1234567890',
    gameName: 'Spring Rally',
    teams: [],
    zones: [],
    ...overrides,
  });

  const team = (locationTime: string) => ({
    teamId: '11111111-1111-1111-1111-111111111111',
    teamName: 'De Watervlooien',
    latitude: 52.1326,
    longitude: 5.2913,
    locationTime,
  });

  const zone = (overrides = {}) => ({
    id: 'z1',
    name: 'Sluis',
    latitude: 52.2,
    longitude: 5.3,
    zoneType: 'circle' as const,
    radiusMeters: 75,
    ...overrides,
  });

  it('never marks a marker as hidden', () => {
    const markers = toMarkers(payload({ teams: [team('2026-04-01T11:59:00Z')], zones: [zone()] }), NOW);

    expect(markers).toHaveLength(2);
    for (const marker of markers) {
      expect(marker.hidden).toBeUndefined();
      expect(Object.keys(marker)).not.toContain('hidden');
    }
  });

  it('maps team positions with the shared team colour', () => {
    const [marker] = toMarkers(payload({ teams: [team('2026-04-01T11:59:00Z')] }), NOW);

    expect(marker).toMatchObject({
      id: '11111111-1111-1111-1111-111111111111',
      label: 'De Watervlooien',
      color: TEAM_COLOR,
      type: 'team',
      lat: 52.1326,
      lng: 5.2913,
    });
  });

  it('marks a team stale once its last fix is older than the threshold', () => {
    const stale = new Date(NOW - STALE_AFTER_MS - 1_000).toISOString();
    const fresh = new Date(NOW - 1_000).toISOString();

    expect(toMarkers(payload({ teams: [team(stale)] }), NOW)[0].stale).toBe(true);
    expect(toMarkers(payload({ teams: [team(fresh)] }), NOW)[0].stale).toBe(false);
  });

  it('does not mark a team stale on an unparseable timestamp', () => {
    // Better to show a marker at full opacity than to imply a fix is old when we can't tell.
    expect(toMarkers(payload({ teams: [team('not-a-date')] }), NOW)[0].stale).toBe(false);
  });

  it('maps zone geometry and falls back to the default orange', () => {
    const [marker] = toMarkers(payload({ zones: [zone()] }), NOW);

    expect(marker).toMatchObject({
      id: 'z1',
      label: 'Sluis',
      type: 'checkpoint',
      zoneType: 'circle',
      radiusMeters: 75,
      color: '#ff9800',
    });
  });

  it('keeps an explicit zone colour', () => {
    const [marker] = toMarkers(payload({ zones: [zone({ color: '#2196f3' })] }), NOW);

    expect(marker.color).toBe('#2196f3');
  });

  it('carries polygon vertices through', () => {
    const points = [[52.1, 5.1], [52.2, 5.1], [52.2, 5.2]];
    const [marker] = toMarkers(
      payload({ zones: [zone({ zoneType: 'polygon' as const, polygonPoints: points })] }),
      NOW,
    );

    expect(marker.polygonPoints).toEqual(points);
  });

  it('skips zones without coordinates', () => {
    const markers = toMarkers(
      payload({ zones: [zone({ latitude: undefined, longitude: undefined })] }),
      NOW,
    );

    expect(markers).toHaveLength(0);
  });
});
