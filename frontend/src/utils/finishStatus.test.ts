import { describe, expect, it } from 'vitest';
import { finishStatusRank, getFinishStatus } from './finishStatus';
import type { Game } from '../types/entities';

const END = '2026-08-05T18:00:00Z';
// endTime is typed as Date but arrives from the API as an ISO string, which is what the util
// has to cope with. No default here: an explicit undefined must stay undefined.
const gameWith = (status: Game['status'], endTime: string | undefined) =>
  ({ status, endTime: endTime as unknown as Date }) as Pick<Game, 'endTime' | 'status'>;
const game = (status: Game['status']) => gameWith(status, END);

describe('getFinishStatus', () => {
  it('reports a finish before the end time as finished', () => {
    expect(getFinishStatus({ finished: true, finishedAt: '2026-08-05T17:30:00Z' }, game('completed')))
      .toBe('finished');
  });

  it('reports a finish after the end time as late', () => {
    expect(getFinishStatus({ finished: true, finishedAt: '2026-08-05T18:20:00Z' }, game('completed')))
      .toBe('late');
  });

  it('treats a finish exactly on the end time as on time', () => {
    expect(getFinishStatus({ finished: true, finishedAt: END }, game('completed'))).toBe('finished');
  });

  it('keeps an unfinished team en route while the game is still running', () => {
    expect(getFinishStatus({ finished: false }, game('active'))).toBe('enRoute');
    expect(getFinishStatus({ finished: false }, game('paused'))).toBe('enRoute');
  });

  it('marks an unfinished team DNF once the game has completed', () => {
    expect(getFinishStatus({ finished: false }, game('completed'))).toBe('dnf');
  });

  it('flips a still-out team from DNF to late when the desk marks it finished', () => {
    // The tracker-handover case: finishedAt is stamped when the admin ticks the box, which is
    // after the end time, so the team lands on late rather than staying DNF.
    const completed = game('completed');
    expect(getFinishStatus({ finished: false }, completed)).toBe('dnf');
    expect(getFinishStatus({ finished: true, finishedAt: '2026-08-05T18:45:00Z' }, completed)).toBe('late');
  });

  it('falls back to finished when either timestamp is missing', () => {
    // Rows that predate finished_at, or a game with no end time, must not be called late.
    expect(getFinishStatus({ finished: true, finishedAt: null }, game('completed'))).toBe('finished');
    expect(getFinishStatus({ finished: true, finishedAt: '2026-08-05T18:20:00Z' }, gameWith('completed', undefined)))
      .toBe('finished');
  });

  it('does not call a team that never checked in DNF', () => {
    // A no-show did not start; the backend does not penalise it either.
    expect(getFinishStatus({ finished: false, checkedIn: false }, game('completed'))).toBe('enRoute');
  });

  it('treats a row without a checkedIn field as checked in', () => {
    // Ranking rows carry no checkedIn — the ranking only ever contains checked-in teams.
    expect(getFinishStatus({ finished: false }, game('completed'))).toBe('dnf');
  });

  it('is en route when no game is loaded yet', () => {
    expect(getFinishStatus({ finished: false }, null)).toBe('enRoute');
    expect(getFinishStatus({ finished: false }, undefined)).toBe('enRoute');
  });
});

describe('finishStatusRank', () => {
  it('sorts least resolved first', () => {
    const completed = game('completed');
    const ranks = [
      finishStatusRank({ finished: false }, game('active')), // enRoute
      finishStatusRank({ finished: false }, completed), // dnf
      finishStatusRank({ finished: true, finishedAt: '2026-08-05T18:20:00Z' }, completed), // late
      finishStatusRank({ finished: true, finishedAt: '2026-08-05T17:20:00Z' }, completed), // finished
    ];
    expect(ranks).toEqual([...ranks].sort((a, b) => a - b));
    expect(new Set(ranks).size).toBe(4);
  });
});
