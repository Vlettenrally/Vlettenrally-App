/**
 * Pure helpers for the admin map's live team-location tracking. Extracted from the
 * AdminMap component so the batching / track-growth / staleness logic can be unit
 * tested without rendering the map.
 */
import type { TeamLocation } from '../../types/entities';

/** teamId → polyline points as [lng, lat] in time-ascending order. */
export type TrackPoints = Record<string, Array<[number, number]>>;

/** Merge incoming fixes into the location list; the newest fix wins per team. */
export function mergeLocations(prev: TeamLocation[], incoming: TeamLocation[]): TeamLocation[] {
  const byId = new Map(prev.map(l => [l.teamId, l]));
  for (const loc of incoming) {
    byId.set(loc.teamId, loc);
  }
  return Array.from(byId.values());
}

/**
 * Append new fixes to the polylines of currently-tracked teams that already have a
 * fetched history. Teams without an existing track are skipped (so a live fix never
 * starts a bare one-point track before its history loads); identical trailing points
 * are skipped too. Returns the same object reference when nothing changed so callers
 * can avoid a needless re-render.
 */
export function appendTrackPoints(
  prev: TrackPoints,
  updates: TeamLocation[],
  trackedIds: ReadonlySet<string>,
): TrackPoints {
  let changed = false;
  const next = { ...prev };
  for (const loc of updates) {
    if (!trackedIds.has(loc.teamId)) continue;
    const existing = next[loc.teamId];
    if (!existing) continue;
    const last = existing[existing.length - 1];
    if (last && last[0] === loc.longitude && last[1] === loc.latitude) continue;
    next[loc.teamId] = [...existing, [loc.longitude, loc.latitude]];
    changed = true;
  }
  return changed ? next : prev;
}

/**
 * Split incoming tracked-team fixes into:
 *  - `staleTracked`: teams whose gap since the last known fix exceeds `staleAfterMs`.
 *    Appending a single point would skip any server-backfilled points, so the whole
 *    track must be refetched.
 *  - `freshUpdates`: small-gap fixes that can just extend the polyline.
 *
 * Non-tracked teams and teams without a valid previous/new timestamp are treated as
 * fresh (the first fix for a team is never "stale").
 */
export function partitionTrackedUpdates(
  incoming: TeamLocation[],
  prevById: Map<string, TeamLocation>,
  trackedIds: ReadonlySet<string>,
  staleAfterMs: number,
): { staleTracked: string[]; freshUpdates: TeamLocation[] } {
  const staleTracked: string[] = [];
  const freshUpdates: TeamLocation[] = [];
  for (const loc of incoming) {
    if (!trackedIds.has(loc.teamId)) continue;
    const prevTs = Date.parse(prevById.get(loc.teamId)?.locationTime ?? '');
    const newTs = Date.parse(loc.locationTime);
    if (!Number.isNaN(prevTs) && !Number.isNaN(newTs) && newTs - prevTs > staleAfterMs) {
      staleTracked.push(loc.teamId);
    } else {
      freshUpdates.push(loc);
    }
  }
  return { staleTracked, freshUpdates };
}

/** Convert a location-history response into polyline points. */
export function toTrackPoints(locations: TeamLocation[]): Array<[number, number]> {
  return locations.map(p => [p.longitude, p.latitude] as [number, number]);
}
