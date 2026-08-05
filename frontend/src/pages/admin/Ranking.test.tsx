/**
 * Tests for the admin Ranking page: it loads the standings once, renders the
 * leaderboard, and folds live RANKING_UPDATE events in while subscribed to the
 * `ranking` admin heartbeat topic.
 */
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { act, cleanup, fireEvent, render, screen, waitFor } from '@testing-library/react';

// Passthrough translations so assertions can target i18n keys / rendered data.
vi.mock('react-i18next', () => ({
  useTranslation: () => ({ t: (key: string) => key }),
}));

// Capture RANKING_UPDATE handlers so tests can fire events at the page.
const ws = vi.hoisted(() => {
  const handlers = new Map<string, Set<(m: unknown) => void>>();
  const on = (type: string, handler: (m: unknown) => void) => {
    if (!handlers.has(type)) handlers.set(type, new Set());
    handlers.get(type)!.add(handler);
    return () => handlers.get(type)!.delete(handler);
  };
  return { handlers, on };
});

vi.mock('../../services/websocket', () => ({
  default: { on: ws.on },
  WebSocketEventType: { RANKING_UPDATE: 'RANKING_UPDATE' },
}));

const getRankings = vi.hoisted(() => vi.fn());
vi.mock('../../services/api', () => ({ default: { getRankings } }));

const useAdminTopicHeartbeat = vi.hoisted(() => vi.fn());
vi.mock('../../hooks/useAdminHeartbeat', () => ({
  useAdminTopicHeartbeat,
  ADMIN_TOPICS: { RANKING: 'ranking' },
}));

vi.mock('../../hooks/useAdminSync', () => ({ useAdminResync: vi.fn() }));

const exportRankings = vi.hoisted(() => vi.fn());
vi.mock('../../services/gameService', () => ({ gameService: { exportRankings } }));

const downloadFile = vi.hoisted(() => vi.fn());
vi.mock('../../utils/download', () => ({ downloadFile }));

import Ranking from './Ranking';
import { useGameStore } from '../../store/useGameStore';
import type { RankingEntry } from '../../types/entities';

function fireRankingUpdate(payload: RankingEntry[]) {
  act(() => {
    ws.handlers.get('RANKING_UPDATE')?.forEach((h) => h({ type: 'RANKING_UPDATE', payload, timestamp: '' }));
  });
}

beforeEach(() => {
  ws.handlers.clear();
  useGameStore.setState({ selectedGameId: 'game-1' });
  getRankings.mockResolvedValue({
    data: [
      { position: 1, teamId: 't1', teamName: 'De Watervlooien', points: 120 },
      { position: 2, teamId: 't2', teamName: 'Windkracht Tien', points: 90 },
    ] as RankingEntry[],
  });
});

afterEach(() => {
  cleanup();
  vi.clearAllMocks();
});

describe('admin Ranking page', () => {
  it('shows the select-a-game prompt when no game is selected', () => {
    useGameStore.setState({ selectedGameId: null });
    render(<Ranking />);
    expect(screen.getByText('admin.ranking.selectGameFirst')).toBeTruthy();
    expect(getRankings).not.toHaveBeenCalled();
  });

  it('loads the initial standings and renders them in order', async () => {
    render(<Ranking />);
    await waitFor(() => expect(screen.getByText('De Watervlooien')).toBeTruthy());
    expect(getRankings).toHaveBeenCalledWith('game-1');
    expect(screen.getByText('Windkracht Tien')).toBeTruthy();
    expect(screen.getByText('120')).toBeTruthy();
  });

  it('subscribes to the ranking heartbeat topic', () => {
    render(<Ranking />);
    expect(useAdminTopicHeartbeat).toHaveBeenCalledWith('ranking');
  });

  it('replaces the standings when a live RANKING_UPDATE arrives', async () => {
    render(<Ranking />);
    await waitFor(() => expect(screen.getByText('De Watervlooien')).toBeTruthy());

    fireRankingUpdate([{ position: 1, teamId: 't2', teamName: 'Windkracht Tien', points: 200 }]);

    expect(screen.getByText('200')).toBeTruthy();
    // The old leader is gone — the update replaced the list rather than appending.
    expect(screen.queryByText('De Watervlooien')).toBeNull();
  });

  it('stops updating after unmount', async () => {
    const { unmount } = render(<Ranking />);
    await waitFor(() => expect(screen.getByText('De Watervlooien')).toBeTruthy());
    unmount();
    fireRankingUpdate([{ position: 1, teamId: 't9', teamName: 'Ghost', points: 5 }]);
    expect(screen.queryByText('Ghost')).toBeNull();
  });

  it('downloads the server-generated CSV for the selected game', async () => {
    // The file is passed through untouched — the server's BOM and delimiter must survive.
    const file = new Blob(['﻿plaats;bootnummer\n1;De Watervlooien\n'], { type: 'text/csv' });
    exportRankings.mockResolvedValue(file);
    render(<Ranking />);

    fireEvent.click(screen.getByRole('button', { name: 'admin.ranking.export' }));

    await waitFor(() => expect(exportRankings).toHaveBeenCalledWith('game-1'));
    expect(downloadFile).toHaveBeenCalledWith(file, 'ranglijst-game-1.csv');
  });

  it('surfaces an export failure instead of downloading', async () => {
    exportRankings.mockRejectedValue(new Error('Export mislukt (403)'));
    render(<Ranking />);

    fireEvent.click(screen.getByRole('button', { name: 'admin.ranking.export' }));

    await waitFor(() => expect(screen.getByText('Export mislukt (403)')).toBeTruthy());
    expect(downloadFile).not.toHaveBeenCalled();
  });
});
