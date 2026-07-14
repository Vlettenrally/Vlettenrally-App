import type { AdminTeam } from '../types/entities';

/**
 * The check-in states a team can be in, as shown to the rally desk. Derived from
 * checkedIn + checkInMethod + checkedInDigitally, which together encode:
 *  - manual: the desk filled in the team's details, so check-in is done;
 *  - digitalPending: the desk sent the team off to their own phones, but they have
 *    not submitted their details yet — the one state the desk needs to chase.
 */
export type CheckInStatus = 'notCheckedIn' | 'manual' | 'digitalPending' | 'digitalCompleted';

type CheckInFields = Pick<AdminTeam, 'checkedIn' | 'checkedInDigitally' | 'checkInMethod'>;

export function getCheckInStatus(team: CheckInFields): CheckInStatus {
  if (!team.checkedIn) return 'notCheckedIn';
  if (team.checkedInDigitally) return 'digitalCompleted';
  if (team.checkInMethod === 'DIGITAL') return 'digitalPending';
  // A checked-in team without a method predates check_in_method; treat it as manual,
  // which is how it was displayed before.
  return 'manual';
}

/** Sort order: least progressed first, so teams still needing attention surface on top. */
const rank: Record<CheckInStatus, number> = {
  notCheckedIn: 0,
  digitalPending: 1,
  manual: 2,
  digitalCompleted: 3,
};

export function checkInStatusRank(team: CheckInFields): number {
  return rank[getCheckInStatus(team)];
}

/** All four states, in the order the rally desk progresses through them. */
export const CHECK_IN_STATUSES: CheckInStatus[] = [
  'notCheckedIn',
  'manual',
  'digitalPending',
  'digitalCompleted',
];

/** The fields to send to the API to put a team in the given state. */
export function checkInStatusToFields(status: CheckInStatus): CheckInFields {
  switch (status) {
    case 'notCheckedIn':
      return { checkedIn: false, checkedInDigitally: false, checkInMethod: null };
    case 'manual':
      return { checkedIn: true, checkedInDigitally: false, checkInMethod: 'MANUAL' };
    case 'digitalPending':
      return { checkedIn: true, checkedInDigitally: false, checkInMethod: 'DIGITAL' };
    case 'digitalCompleted':
      return { checkedIn: true, checkedInDigitally: true, checkInMethod: 'DIGITAL' };
  }
}
