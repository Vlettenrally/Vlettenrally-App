/**
 * Tests for the admin map's live-tracking helpers: merging batched location fixes,
 * growing tracked-team polylines, and deciding when a gap is large enough that the
 * whole track must be refetched instead of appended.
 */
import { describe, expect, it } from 'vitest';
import {
  appendTrackPoints,
  mergeLocations,
  partitionTrackedUpdates,
  toTrackPoints,
  type TrackPoints,
} from './trackUtils';
import type { TeamLocation } from '../../types/entities';

const STALE_AFTER_MS = 5 * 60_000;

function loc(overrides: Partial<TeamLocation> & { teamId: string }): TeamLocation {
  return {
    teamName: `Team ${overrides.teamId}`,
    latitude: 52,
    longitude: 4,
    locationTime: '2026-07-10T12:00:00.000Z',
    ...overrides,
  };
}

describe('mergeLocations', () => {
  it('adds new teams and keeps existing ones', () => {
    const prev = [loc({ teamId: 'a' })];
    const result = mergeLocations(prev, [loc({ teamId: 'b' })]);
    expect(result.map(l => l.teamId).sort()).toEqual(['a', 'b']);
  });

  it('replaces an existing team with the newest fix', () => {
    const prev = [loc({ teamId: 'a', latitude: 52, longitude: 4 })];
    const result = mergeLocations(prev, [loc({ teamId: 'a', latitude: 53, longitude: 5 })]);
    expect(result).toHaveLength(1);
    expect(result[0]).toMatchObject({ teamId: 'a', latitude: 53, longitude: 5 });
  });

  it('applies the last fix when a batch has duplicates for a team', () => {
    const result = mergeLocations([], [
      loc({ teamId: 'a', longitude: 4 }),
      loc({ teamId: 'a', longitude: 6 }),
    ]);
    expect(result).toHaveLength(1);
    expect(result[0].longitude).toBe(6);
  });
});

describe('appendTrackPoints', () => {
  const tracked = new Set(['a']);

  it('appends a new point to a tracked team with an existing track', () => {
    const prev: TrackPoints = { a: [[4, 52]] };
    const result = appendTrackPoints(prev, [loc({ teamId: 'a', longitude: 5, latitude: 53 })], tracked);
    expect(result.a).toEqual([[4, 52], [5, 53]]);
  });

  it('does not start a track for a tracked team without a fetched history', () => {
    const result = appendTrackPoints({}, [loc({ teamId: 'a', longitude: 5, latitude: 53 })], tracked);
    expect(result.a).toBeUndefined();
  });

  it('ignores teams that are not tracked', () => {
    const prev: TrackPoints = { b: [[4, 52]] };
    const result = appendTrackPoints(prev, [loc({ teamId: 'b', longitude: 5, latitude: 53 })], tracked);
    expect(result).toBe(prev); // untouched, same reference
    expect(result.b).toEqual([[4, 52]]);
  });

  it('skips an identical trailing point and returns the same reference', () => {
    const prev: TrackPoints = { a: [[4, 52]] };
    const result = appendTrackPoints(prev, [loc({ teamId: 'a', longitude: 4, latitude: 52 })], tracked);
    expect(result).toBe(prev);
  });

  it('returns a new object only when something changed', () => {
    const prev: TrackPoints = { a: [[4, 52]] };
    const changed = appendTrackPoints(prev, [loc({ teamId: 'a', longitude: 5, latitude: 53 })], tracked);
    expect(changed).not.toBe(prev);
    // Non-mutating: original array is untouched.
    expect(prev.a).toEqual([[4, 52]]);
  });

  it('appends multiple tracked teams in one batch', () => {
    const prev: TrackPoints = { a: [[4, 52]], b: [[3, 51]] };
    const result = appendTrackPoints(
      prev,
      [loc({ teamId: 'a', longitude: 5, latitude: 53 }), loc({ teamId: 'b', longitude: 6, latitude: 54 })],
      new Set(['a', 'b']),
    );
    expect(result.a).toEqual([[4, 52], [5, 53]]);
    expect(result.b).toEqual([[3, 51], [6, 54]]);
  });
});

describe('partitionTrackedUpdates', () => {
  const tracked = new Set(['a']);

  it('treats a small gap as a fresh update', () => {
    const prevById = new Map([['a', loc({ teamId: 'a', locationTime: '2026-07-10T12:00:00.000Z' })]]);
    const incoming = [loc({ teamId: 'a', locationTime: '2026-07-10T12:04:00.000Z' })]; // 4 min gap
    const { staleTracked, freshUpdates } = partitionTrackedUpdates(incoming, prevById, tracked, STALE_AFTER_MS);
    expect(staleTracked).toEqual([]);
    expect(freshUpdates.map(l => l.teamId)).toEqual(['a']);
  });

  it('flags a gap larger than the threshold as stale (needs refetch)', () => {
    const prevById = new Map([['a', loc({ teamId: 'a', locationTime: '2026-07-10T12:00:00.000Z' })]]);
    const incoming = [loc({ teamId: 'a', locationTime: '2026-07-10T12:06:00.000Z' })]; // 6 min gap
    const { staleTracked, freshUpdates } = partitionTrackedUpdates(incoming, prevById, tracked, STALE_AFTER_MS);
    expect(staleTracked).toEqual(['a']);
    expect(freshUpdates).toEqual([]);
  });

  it('treats a gap exactly at the threshold as fresh (strictly greater required)', () => {
    const prevById = new Map([['a', loc({ teamId: 'a', locationTime: '2026-07-10T12:00:00.000Z' })]]);
    const incoming = [loc({ teamId: 'a', locationTime: '2026-07-10T12:05:00.000Z' })]; // exactly 5 min
    const { staleTracked, freshUpdates } = partitionTrackedUpdates(incoming, prevById, tracked, STALE_AFTER_MS);
    expect(staleTracked).toEqual([]);
    expect(freshUpdates.map(l => l.teamId)).toEqual(['a']);
  });

  it('treats the first fix (no previous) as fresh, never stale', () => {
    const incoming = [loc({ teamId: 'a', locationTime: '2026-07-10T12:00:00.000Z' })];
    const { staleTracked, freshUpdates } = partitionTrackedUpdates(incoming, new Map(), tracked, STALE_AFTER_MS);
    expect(staleTracked).toEqual([]);
    expect(freshUpdates.map(l => l.teamId)).toEqual(['a']);
  });

  it('ignores updates for teams that are not tracked', () => {
    const prevById = new Map([['b', loc({ teamId: 'b', locationTime: '2026-07-10T12:00:00.000Z' })]]);
    const incoming = [loc({ teamId: 'b', locationTime: '2026-07-10T12:30:00.000Z' })]; // large gap but untracked
    const { staleTracked, freshUpdates } = partitionTrackedUpdates(incoming, prevById, tracked, STALE_AFTER_MS);
    expect(staleTracked).toEqual([]);
    expect(freshUpdates).toEqual([]);
  });

  it('splits a mixed batch into stale and fresh', () => {
    const prevById = new Map([
      ['a', loc({ teamId: 'a', locationTime: '2026-07-10T12:00:00.000Z' })],
      ['b', loc({ teamId: 'b', locationTime: '2026-07-10T12:00:00.000Z' })],
    ]);
    const incoming = [
      loc({ teamId: 'a', locationTime: '2026-07-10T12:10:00.000Z' }), // 10 min → stale
      loc({ teamId: 'b', locationTime: '2026-07-10T12:02:00.000Z' }), // 2 min → fresh
    ];
    const { staleTracked, freshUpdates } = partitionTrackedUpdates(
      incoming, prevById, new Set(['a', 'b']), STALE_AFTER_MS,
    );
    expect(staleTracked).toEqual(['a']);
    expect(freshUpdates.map(l => l.teamId)).toEqual(['b']);
  });
});

describe('toTrackPoints', () => {
  it('maps history fixes to [lng, lat] pairs in order', () => {
    const points = toTrackPoints([
      loc({ teamId: 'a', longitude: 4, latitude: 52 }),
      loc({ teamId: 'a', longitude: 5, latitude: 53 }),
    ]);
    expect(points).toEqual([[4, 52], [5, 53]]);
  });

  it('returns an empty array for no fixes', () => {
    expect(toTrackPoints([])).toEqual([]);
  });
});
