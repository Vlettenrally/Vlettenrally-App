import type { AdminTeam } from '../types/entities';

/**
 * Whether a team's tracker has been physically returned. 'noTracker' is distinct from
 * 'notHandedIn' so the Teams table can stay quiet for teams that were never assigned one.
 */
export type TrackerHandInStatus = 'noTracker' | 'notHandedIn' | 'handedIn';

type Fields = Pick<AdminTeam, 'trackerId' | 'trackerHandedIn'>;

export function getTrackerHandInStatus(team: Fields): TrackerHandInStatus {
  if (!team.trackerId) return 'noTracker';
  return team.trackerHandedIn ? 'handedIn' : 'notHandedIn';
}

const rank: Record<TrackerHandInStatus, number> = { noTracker: 0, notHandedIn: 1, handedIn: 2 };

export function trackerHandInStatusRank(team: Fields): number {
  return rank[getTrackerHandInStatus(team)];
}
