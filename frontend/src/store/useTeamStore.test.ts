import { beforeEach, describe, expect, it } from 'vitest';
import { useTeamStore, type Achievement } from './useTeamStore';
import type { PlayerTeam } from '../types/entities';

function achievement(id: string): Achievement {
  return {
    id,
    teamId: 'team-1',
    checkpointId: 'cp-1',
    pointsEarned: 10,
    achievedAt: '2026-06-24T12:00:00.000Z',
  };
}

beforeEach(() => {
  useTeamStore.getState().reset();
});

describe('addAchievement', () => {
  it('appends a new achievement', () => {
    const { addAchievement } = useTeamStore.getState();
    addAchievement(achievement('a'));
    addAchievement(achievement('b'));
    expect(useTeamStore.getState().achievements.map((a) => a.id)).toEqual(['a', 'b']);
  });

  it('dedupes by id so a hydrate-then-WS-event race cannot double-count', () => {
    const { addAchievement } = useTeamStore.getState();
    addAchievement(achievement('a'));
    const before = useTeamStore.getState().achievements;
    addAchievement(achievement('a'));
    const after = useTeamStore.getState().achievements;
    expect(after).toBe(before); // unchanged reference
    expect(after).toHaveLength(1);
  });
});

describe('updatePoints', () => {
  it('is a no-op when there is no team', () => {
    useTeamStore.getState().updatePoints(50);
    expect(useTeamStore.getState().team).toBeNull();
  });

  it('updates totalPoints without dropping other team fields', () => {
    const team = { id: 'team-1', name: 'De Watervlooien', totalPoints: 10 } as PlayerTeam;
    useTeamStore.getState().setTeam(team);
    useTeamStore.getState().updatePoints(75);
    const updated = useTeamStore.getState().team!;
    expect(updated.totalPoints).toBe(75);
    expect(updated.name).toBe('De Watervlooien');
    expect(updated.id).toBe('team-1');
  });
});

describe('simple setters', () => {
  it('replace their slice wholesale', () => {
    const rankings = [{ teamId: 't1' }, { teamId: 't2' }] as never[];
    useTeamStore.getState().setRankings(rankings);
    expect(useTeamStore.getState().rankings).toBe(rankings);

    useTeamStore.getState().setAchievements([achievement('x')]);
    expect(useTeamStore.getState().achievements.map((a) => a.id)).toEqual(['x']);
  });
});
