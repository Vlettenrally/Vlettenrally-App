/**
 * Tests for useTeamSync: a throttled, guarded full-state refetch triggered by
 * reconnect / page-visible / online. We mock the services it calls and count
 * api.getMessages invocations as a proxy for "a resync ran".
 */
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { cleanup, renderHook } from '@testing-library/react';

// Controllable websocket connection-state listener registry.
const wsSync = vi.hoisted(() => {
  const listeners = new Set<(connected: boolean) => void>();
  return {
    listeners,
    websocket: {
      onConnectionChange: (cb: (c: boolean) => void) => {
        listeners.add(cb);
        return () => listeners.delete(cb);
      },
    },
  };
});
vi.mock('../services/websocket', () => ({ websocket: wsSync.websocket }));

const apiMock = vi.hoisted(() => ({
  getMessages: vi.fn(async () => ({ data: [] })),
  getTeamVerifiedAchievements: vi.fn(async () => ({ data: [] })),
  getRivalTeams: vi.fn(async () => ({ data: [] })),
  getCheckpoints: vi.fn(async () => ({ data: [] })),
  getRankings: vi.fn(async () => ({ data: [] })),
}));
vi.mock('../services/api', () => ({ default: apiMock }));

const teamServiceMock = vi.hoisted(() => ({ getMyTeam: vi.fn(async () => null) }));
vi.mock('../services/teamService', () => ({ default: teamServiceMock }));

// fetchGameById -> gameService.getGame; control rankingsPublished from here.
const gameServiceMock = vi.hoisted(() => ({
  getGame: vi.fn(async () => ({ id: 'g', rankingsPublished: false })),
  getGames: vi.fn(async () => []),
}));
vi.mock('../services/gameService', () => ({ default: gameServiceMock, gameService: gameServiceMock }));

import useTeamSync from './useTeamSync';

const flush = async () => {
  for (let i = 0; i < 10; i++) await Promise.resolve();
};

function fireConnection(connected: boolean) {
  wsSync.listeners.forEach((cb) => cb(connected));
}

function setOnline(online: boolean) {
  Object.defineProperty(navigator, 'onLine', { configurable: true, value: online });
}

beforeEach(() => {
  vi.useFakeTimers();
  vi.setSystemTime(new Date('2026-06-24T12:00:00.000Z'));
  vi.clearAllMocks();
  wsSync.listeners.clear();
  gameServiceMock.getGame.mockResolvedValue({ id: 'g', rankingsPublished: false });
  setOnline(true);
});

afterEach(() => {
  cleanup();
  vi.useRealTimers();
});

it('resyncs on a disconnected -> connected transition, but not on the initial connect', async () => {
  renderHook(() => useTeamSync({ gameId: 'g', teamId: 't' }));

  // Initial connect (no preceding disconnect) must not resync.
  fireConnection(true);
  await flush();
  expect(apiMock.getMessages).not.toHaveBeenCalled();

  // A real reconnect (disconnected -> connected) resyncs.
  fireConnection(false);
  fireConnection(true);
  await flush();
  expect(apiMock.getMessages).toHaveBeenCalledTimes(1);
});

it('throttles repeat triggers within the 5s window, then allows one after it', async () => {
  renderHook(() => useTeamSync({ gameId: 'g', teamId: 't' }));

  fireConnection(false);
  fireConnection(true);
  await flush();
  expect(apiMock.getMessages).toHaveBeenCalledTimes(1);

  // Within 5s -> skipped by the throttle.
  document.dispatchEvent(new Event('visibilitychange'));
  await flush();
  expect(apiMock.getMessages).toHaveBeenCalledTimes(1);

  // After the window -> allowed.
  vi.advanceTimersByTime(5001);
  document.dispatchEvent(new Event('visibilitychange'));
  await flush();
  expect(apiMock.getMessages).toHaveBeenCalledTimes(2);
});

it('does not resync while offline', async () => {
  setOnline(false);
  renderHook(() => useTeamSync({ gameId: 'g', teamId: 't' }));

  fireConnection(false);
  fireConnection(true);
  await flush();

  expect(apiMock.getMessages).not.toHaveBeenCalled();
});

describe('rankings gate', () => {
  it('skips getRankings when rankings are not published', async () => {
    gameServiceMock.getGame.mockResolvedValue({ id: 'g', rankingsPublished: false });
    renderHook(() => useTeamSync({ gameId: 'g', teamId: 't' }));

    fireConnection(false);
    fireConnection(true);
    await flush();

    expect(apiMock.getMessages).toHaveBeenCalledTimes(1);
    expect(apiMock.getRankings).not.toHaveBeenCalled();
  });

  it('fetches rankings once they are published', async () => {
    gameServiceMock.getGame.mockResolvedValue({ id: 'g', rankingsPublished: true });
    renderHook(() => useTeamSync({ gameId: 'g', teamId: 't' }));

    fireConnection(false);
    fireConnection(true);
    await flush();

    expect(apiMock.getRankings).toHaveBeenCalledTimes(1);
  });
});
