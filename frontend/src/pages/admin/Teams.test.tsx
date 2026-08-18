/**
 * Tests for how the admin Teams page reacts to team:* WebSocket events.
 *
 * The page must apply the event payload to its own state rather than refetching the list:
 * during desk check-in every team fires team:updated, and a refetch per event meant one full
 * fetch of all teams per event, per open tab. The list is only fetched on mount (and on resync,
 * which useAdminSync drives separately).
 */
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { act, cleanup, render, screen, waitFor } from '@testing-library/react';
import type { AdminTeam } from '../../types/entities';

vi.mock('react-i18next', () => ({
  useTranslation: () => ({
    t: (key: string, opts?: Record<string, unknown>) =>
      opts?.count !== undefined ? `${key}:${opts.count}` : key,
    i18n: { language: 'en' },
  }),
}));

const getTeamsByGame = vi.hoisted(() => vi.fn());
vi.mock('../../services/teamService', () => ({
  teamService: { getTeamsByGame, getTeam: vi.fn() },
}));

const getGame = vi.hoisted(() => vi.fn());
vi.mock('../../services/gameService', () => ({
  gameService: { getGame, importTeams: vi.fn(), assignTrackers: vi.fn() },
}));

// Capture the handlers the page registers so tests can deliver frames to them directly.
const handlers = vi.hoisted(() => new Map<string, (m: unknown) => void>());
const sent = vi.hoisted(() => [] as unknown[]);
vi.mock('../../services/websocket', () => ({
  WebSocketEventType: { RANKING_UPDATE: 'RANKING_UPDATE' },
  websocket: {
    on: (type: string, handler: (m: unknown) => void) => {
      handlers.set(type, handler);
      return () => handlers.delete(type);
    },
    // Used by useWebSocketSubscription, which backs useAdminTopicHeartbeat.
    isConnected: () => true,
    onConnectionChange: () => () => {},
    send: (message: unknown) => sent.push(message),
  },
}));

vi.mock('../../store/useGameStore', () => ({
  useGameStore: (selector: (s: { selectedGameId: string }) => unknown) =>
    selector({ selectedGameId: 'game-1' }),
}));

vi.mock('../../hooks/useAdminSync', () => ({ useAdminResync: vi.fn() }));

// Dialogs pull in editors and a map; the list behaviour under test does not need them.
vi.mock('../../components/TeamDetailDialog', () => ({ default: () => null }));
vi.mock('../../components/TrackerExportDialog', () => ({ default: () => null }));

import Teams from './Teams';

const team = (id: string, name: string, over: Partial<AdminTeam> = {}): AdminTeam => ({
  id,
  gameId: 'game-1',
  name,
  description: 'desc',
  participants: [],
  checkedIn: false,
  checkedInDigitally: false,
  finished: false,
  freeClass: false,
  disqualified: false,
  traccarEnabled: false,
  trackerHandedIn: false,
  totalPoints: 0,
  rivals: [],
  createdAt: '2026-07-14T10:00:00Z',
  updatedAt: '2026-07-14T10:00:00Z',
  ...over,
});

const emit = async (type: string, payload: unknown) => {
  await act(async () => {
    handlers.get(type)?.({ type, payload, timestamp: '2026-07-14T10:00:00Z' });
  });
};

describe('admin Teams page, team:* events', () => {
  beforeEach(() => {
    cleanup();
    handlers.clear();
    sent.length = 0;
    vi.clearAllMocks();
    getGame.mockResolvedValue({ id: 'game-1', status: 'active' });
    getTeamsByGame.mockResolvedValue([team('t1', 'Alpha'), team('t2', 'Bravo')]);
  });

  const renderPage = async () => {
    render(<Teams />);
    await waitFor(() => expect(screen.getByText('Alpha')).toBeTruthy());
  };

  it('applies team:updated to the row without refetching the list', async () => {
    await renderPage();
    expect(getTeamsByGame).toHaveBeenCalledTimes(1);

    await emit('team:updated', team('t1', 'Alpha', { totalPoints: 120, checkedIn: true }));

    expect(screen.getByText('120')).toBeTruthy();
    expect(getTeamsByGame).toHaveBeenCalledTimes(1); // still just the mount fetch
  });

  it('does not refetch once per event when the whole field checks in', async () => {
    const field = Array.from({ length: 250 }, (_, i) => team(`t${i}`, `Team ${i}`));
    getTeamsByGame.mockResolvedValue(field);
    render(<Teams />);
    await waitFor(() => expect(screen.getByText('Team 0')).toBeTruthy());
    expect(getTeamsByGame).toHaveBeenCalledTimes(1);

    // Delivered in one flush purely to keep jsdom from re-rendering a 250-row table 250
    // times. The assertion is unaffected: the refetch this replaced ran synchronously
    // inside the handler, so it would have fired 250 times regardless of React batching.
    await act(async () => {
      for (const t of field) {
        handlers.get('team:updated')?.({
          type: 'team:updated',
          payload: { ...t, checkedIn: true },
          timestamp: '2026-07-14T10:00:00Z',
        });
      }
    });

    // The whole point: 250 events, still only the fetch from mount.
    expect(getTeamsByGame).toHaveBeenCalledTimes(1);
    expect(screen.getByText('admin.teams.teamsLoaded:250')).toBeTruthy();
  });

  it('adds a team on team:created and drops one on team:deleted', async () => {
    await renderPage();

    await emit('team:created', team('t3', 'Charlie'));
    expect(screen.getByText('Charlie')).toBeTruthy();

    await emit('team:deleted', { id: 't1' });
    expect(screen.queryByText('Alpha')).toBeNull();
    expect(screen.getByText('Bravo')).toBeTruthy();
    expect(getTeamsByGame).toHaveBeenCalledTimes(1);
  });

  it('upserts rather than appends, so a repeated team:created cannot double a row', async () => {
    await renderPage();

    await emit('team:created', team('t3', 'Charlie'));
    await emit('team:created', team('t3', 'Charlie'));

    expect(screen.getAllByText('Charlie')).toHaveLength(1);
  });

  it('subscribes to the ranking topic so the backend sends standings at all', async () => {
    await renderPage();

    expect(sent).toContainEqual({ type: 'ADMIN_HEARTBEAT', topic: 'ranking' });
  });

  it('updates the points column from RANKING_UPDATE, without refetching', async () => {
    await renderPage();

    await emit('RANKING_UPDATE', [
      { position: 1, teamId: 't1', teamName: 'Alpha', points: 340 },
      { position: 2, teamId: 't2', teamName: 'Bravo', points: 120 },
    ]);

    expect(screen.getByText('340')).toBeTruthy();
    expect(screen.getByText('120')).toBeTruthy();
    expect(getTeamsByGame).toHaveBeenCalledTimes(1);
  });

  it('leaves teams absent from the ranking alone rather than zeroing them', async () => {
    getTeamsByGame.mockResolvedValue([
      team('t1', 'Alpha', { totalPoints: 50, checkedIn: true }),
      team('t2', 'Bravo', { totalPoints: 70 }), // not checked in -> absent from the ranking
    ]);
    await renderPage();

    await emit('RANKING_UPDATE', [
      { position: 1, teamId: 't1', teamName: 'Alpha', points: 340 },
    ]);

    expect(screen.getByText('340')).toBeTruthy();
    expect(screen.getByText('70')).toBeTruthy(); // untouched, not reset to 0
  });

  it('ignores a frame for a different game', async () => {
    await renderPage();

    await emit('team:updated', team('t1', 'Renamed', { gameId: 'other-game' }));

    expect(screen.getByText('Alpha')).toBeTruthy();
    expect(screen.queryByText('Renamed')).toBeNull();
  });
});
