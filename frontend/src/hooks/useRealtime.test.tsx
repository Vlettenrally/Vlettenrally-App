/**
 * Tests for useRealtime: WebSocket events are translated into Zustand store
 * mutations. We mock ./useWebSocket to capture the subscribed handlers and fire
 * events at them, then assert against the real stores.
 */
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { act, renderHook } from '@testing-library/react';

// Capture handlers registered via subscribe() so tests can fire events.
const ws = vi.hoisted(() => {
  const handlers = new Map<string, Set<(m: unknown) => void>>();
  const subscribe = (type: string, handler: (m: unknown) => void) => {
    if (!handlers.has(type)) handlers.set(type, new Set());
    handlers.get(type)!.add(handler);
    return () => handlers.get(type)!.delete(handler);
  };
  return { handlers, subscribe };
});

vi.mock('./useWebSocket', () => ({
  useTeamWebSocket: () => ({ subscribe: ws.subscribe, connected: true }),
  useAdminWebSocket: () => ({ subscribe: ws.subscribe, connected: true }),
}));

// Mock only teamService so the async refetch handlers never hit the network.
// The real storage module is localStorage-backed and works fine under jsdom.
vi.mock('../services/teamService', () => ({
  default: { getMyTeam: vi.fn(), getTeam: vi.fn() },
}));

import { useTeamRealtime, useAdminRealtime } from './useRealtime';
import { useMessageStore } from '../store/useMessageStore';
import { useTeamStore } from '../store/useTeamStore';
import { useGameStore } from '../store/useGameStore';
import { usePointEntryStore } from '../store/usePointEntryStore';
import type { PlayerTeam, Game, PointEntry } from '../types/entities';

function fire(type: string, payload: unknown) {
  act(() => {
    ws.handlers.get(type)?.forEach((h) => h({ type, payload, timestamp: '' }));
  });
}

beforeEach(() => {
  ws.handlers.clear();
  useMessageStore.getState().reset();
  useTeamStore.getState().reset();
  useGameStore.getState().reset();
  usePointEntryStore.getState().reset();
});

afterEach(() => {
  vi.clearAllMocks();
});

describe('useTeamRealtime', () => {
  it('MESSAGE_NEW adds the message to the store', () => {
    renderHook(() => useTeamRealtime({ gameId: 'g', teamId: 't' }));
    fire('MESSAGE_NEW', {
      id: 'm1',
      teamId: 't',
      messageType: 'general',
      content: 'hi',
      sentAt: '2026-06-24T12:00:00.000Z',
      delivered: true,
      read: false,
    });
    expect(useMessageStore.getState().messages.map((m) => m.id)).toEqual(['m1']);
  });

  it('RANKING_UPDATE sets rankings and flips the rankingsPublished gate', () => {
    useGameStore.getState().setGame({ id: 'g', rankingsPublished: false } as Game);
    renderHook(() => useTeamRealtime({ gameId: 'g', teamId: 't' }));

    fire('RANKING_UPDATE', [{ teamId: 't', points: 10 }]);

    expect(useTeamStore.getState().rankings).toHaveLength(1);
    expect(useGameStore.getState().game?.rankingsPublished).toBe(true);
  });

  // KNOWN BUG: TEAM_ACHIEVEMENT calls updatePoints() unconditionally, but
  // addAchievement() dedupes by id. A duplicate event therefore does NOT add a
  // second achievement, yet it STILL overwrites totalPoints — so a stale/replayed
  // event can clobber the displayed points. Asserting current behavior.
  it('TEAM_ACHIEVEMENT dedupes the achievement but still overwrites points (known bug)', () => {
    useTeamStore.getState().setTeam({ id: 't', name: 'Team', totalPoints: 0 } as PlayerTeam);
    renderHook(() => useTeamRealtime({ gameId: 'g', teamId: 't' }));

    const base = {
      id: 'ach-1',
      teamId: 't',
      checkpointId: 'cp-1',
      pointsEarned: 10,
      achievedAt: '2026-06-24T12:00:00.000Z',
    };
    fire('TEAM_ACHIEVEMENT', { ...base, totalPoints: 50 });
    fire('TEAM_ACHIEVEMENT', { ...base, totalPoints: 999 }); // duplicate id

    expect(useTeamStore.getState().achievements).toHaveLength(1); // deduped
    expect(useTeamStore.getState().team?.totalPoints).toBe(999); // BUG: points still clobbered
  });

  it('stops mutating stores after unmount', () => {
    const { unmount } = renderHook(() => useTeamRealtime({ gameId: 'g', teamId: 't' }));
    unmount();
    fire('MESSAGE_NEW', {
      id: 'm1',
      teamId: 't',
      messageType: 'general',
      content: 'hi',
      sentAt: '2026-06-24T12:00:00.000Z',
      delivered: true,
      read: false,
    });
    expect(useMessageStore.getState().messages).toHaveLength(0);
  });
});

describe('useAdminRealtime', () => {
  it('POINT_ENTRY_CREATED and POINT_ENTRY_UPDATED feed the point-entry store', () => {
    usePointEntryStore.getState().setEntries([], { page: 0, pageSize: 25, totalCount: 0, totalPages: 0 });
    renderHook(() => useAdminRealtime({ gameId: 'g' }));

    fire('POINT_ENTRY_CREATED', { id: 'p1', createdAt: '2026-06-24T10:00:00.000Z' } as PointEntry);
    expect(usePointEntryStore.getState().entries.map((e) => e.id)).toEqual(['p1']);

    fire('POINT_ENTRY_UPDATED', { id: 'p1', createdAt: '2026-06-24T11:00:00.000Z' } as PointEntry);
    expect(usePointEntryStore.getState().entries[0].createdAt).toBe('2026-06-24T11:00:00.000Z');
  });
});
