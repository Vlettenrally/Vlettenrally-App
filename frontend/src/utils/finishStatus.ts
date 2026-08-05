import type { Game } from '../types/entities';

/**
 * The finish states a team can be in, as shown on the teams and ranking pages. Derived from
 * finishedAt plus the game's endTime and status, which together encode:
 *  - late: the team did finish, but only after the clock ran out — it takes the game's flat
 *    late-finish penalty (applied server-side by LateFinishPenaltyService);
 *  - dnf: the game is over and the team never handed in, i.e. it is still out there. A team
 *    still on the water when the game ends shows dnf immediately — and is charged the penalty
 *    at that moment — then flips to late once the desk marks it finished.
 *
 * A team that never checked in did not start, which is a different thing from a DNF, so it stays
 * blank. That matches who the backend charges and what the ranking CSV records. Callers whose
 * rows carry no checkedIn field (the ranking, which only ever contains checked-in teams) are
 * treated as checked in.
 */
export type FinishStatus = 'enRoute' | 'finished' | 'late' | 'dnf';

type FinishFields = { finished?: boolean; finishedAt?: string | null; checkedIn?: boolean };
type GameFields = Pick<Game, 'endTime' | 'status'> | null | undefined;

export function getFinishStatus(team: FinishFields, game: GameFields): FinishStatus {
  if (team.checkedIn === false) return 'enRoute';
  if (team.finished) {
    const endTime = game?.endTime ? new Date(game.endTime).getTime() : null;
    const finishedAt = team.finishedAt ? new Date(team.finishedAt).getTime() : null;
    // A finish without a usable timestamp on either side predates finished_at (or the game has
    // no end time); treat it as an on-time finish, which is how it was displayed before.
    if (endTime !== null && finishedAt !== null && finishedAt > endTime) return 'late';
    return 'finished';
  }
  return game?.status === 'completed' ? 'dnf' : 'enRoute';
}

/** Sort order: least resolved first, so teams still needing attention surface on top. */
const rank: Record<FinishStatus, number> = {
  enRoute: 0,
  dnf: 1,
  late: 2,
  finished: 3,
};

export function finishStatusRank(team: FinishFields, game: GameFields): number {
  return rank[getFinishStatus(team, game)];
}
