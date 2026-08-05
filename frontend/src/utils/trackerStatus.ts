/**
 * Tracker device-health helpers (last seen, battery), shared by the admin Trackers
 * pool page and the Check-in/Checkout desk flows.
 *
 * TRACKER_STALE_AFTER_MS is intentionally separate from mapConstants.ts's
 * STALE_AFTER_MS: that one flags a team's most recent *location* going stale (5 min,
 * a map-freshness concern); this one flags a tracker device going silent (1 hour, a
 * hardware-health concern). Don't merge them.
 */

export const TRACKER_STALE_AFTER_MS = 60 * 60_000; // 1 hour

export function isTrackerStale(lastSeenAt: string | null): boolean {
  if (!lastSeenAt) return false;
  return Date.now() - new Date(lastSeenAt).getTime() > TRACKER_STALE_AFTER_MS;
}

export function batteryColor(percentage: number): 'success' | 'warning' | 'error' {
  if (percentage <= 15) return 'error';
  if (percentage <= 35) return 'warning';
  return 'success';
}
