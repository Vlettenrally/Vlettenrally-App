/**
 * useTeamSync - Re-syncs player state when the app resumes or reconnects.
 *
 * The player shell and pages fetch their data once on mount and then rely on
 * incremental WebSocket events. While the app is offline or suspended (screen
 * locked, PWA backgrounded, tab hidden) the socket drops and any events fired
 * during the gap are missed; the socket reconnects but nothing refetches, so
 * data stays stale until a remount.
 *
 * This hook performs a single, throttled full-state refetch into the global
 * Zustand stores when:
 *   - the WebSocket transitions from disconnected -> connected (reconnect),
 *   - the page becomes visible again (Page Visibility API),
 *   - the browser fires the 'online' event.
 *
 * It is mounted once in TeamLayout so it runs for the whole player app, not
 * per page. It is event-driven, not a standing poll loop.
 */
import {useCallback, useEffect, useRef} from 'react';
import {websocket} from '../services/websocket';
import teamService from '../services/teamService';
import api from '../services/api';
import {type Achievement, type RankingEntry, useTeamStore} from '../store/useTeamStore';
import {useGameStore} from '../store/useGameStore';
import {type Message, useMessageStore} from '../store/useMessageStore';
import type {PublicTeam} from '../types/entities';

interface UseTeamSyncOptions {
  gameId: string | null;
  teamId: string | null;
}

/** Skip a resync if one finished within this window (collapses duplicate triggers). */
const RESYNC_THROTTLE_MS = 5000;

export function useTeamSync({ gameId, teamId }: UseTeamSyncOptions) {
  const isSyncing = useRef(false);
  const lastSyncAt = useRef(0);

  const resyncPlayerState = useCallback(async () => {
    if (!gameId || !teamId) return;
    // Offline: a NetworkFirst fetch would just serve stale cache. Wait for the
    // 'online'/reconnect trigger instead.
    if (typeof navigator !== 'undefined' && !navigator.onLine) return;
    if (isSyncing.current) return;
    if (Date.now() - lastSyncAt.current < RESYNC_THROTTLE_MS) return;

    isSyncing.current = true;
    try {
      // Refresh the game first so the rankings gating below sees fresh status.
      await useGameStore.getState().fetchGameById(gameId).catch((e) => {
        console.warn('Resync: failed to refresh game', e);
      });

      await Promise.allSettled([
        teamService.getMyTeam().then((team) => {
          if (team) useTeamStore.getState().setTeam(team);
        }),
        api.getMessages(teamId).then((res) => {
          if (res.data) useMessageStore.getState().setMessages(res.data as Message[]);
        }),
        api.getTeamVerifiedAchievements(teamId).then((res) => {
          if (res.data) useTeamStore.getState().setAchievements(res.data as Achievement[]);
        }),
        api.getRivalTeams(teamId).then((res) => {
          if (res.data) useTeamStore.getState().setRivalTeams(res.data as PublicTeam[]);
        }),
        api.getCheckpoints(gameId).then((res) => {
          if (res.data) useGameStore.getState().setCheckpoints(res.data);
        }),
        // Rankings are only shown once an admin has revealed them; mirror that
        // gate to avoid a needless query before the reveal.
        useGameStore.getState().game?.rankingsPublished
          ? api.getRankings(gameId).then((res) => {
              if (res.data) useTeamStore.getState().setRankings(res.data as RankingEntry[]);
            })
          : Promise.resolve(),
      ]);
    } finally {
      lastSyncAt.current = Date.now();
      isSyncing.current = false;
    }
  }, [gameId, teamId]);

  // Reconnect trigger: fire only on a disconnected -> connected transition, so
  // the initial connect after mount (already covered by mount fetches) is skipped.
  useEffect(() => {
    if (!gameId || !teamId) return;
    let wasDisconnected = false;
    return websocket.onConnectionChange((connected) => {
      if (!connected) {
        wasDisconnected = true;
      } else if (wasDisconnected) {
        wasDisconnected = false;
        void resyncPlayerState();
      }
    });
  }, [gameId, teamId, resyncPlayerState]);

  // Resume triggers: page becomes visible again, or the browser comes online.
  useEffect(() => {
    if (!gameId || !teamId) return;
    const handleVisibility = () => {
      if (!document.hidden) void resyncPlayerState();
    };
    const handleOnline = () => {
      void resyncPlayerState();
    };
    document.addEventListener('visibilitychange', handleVisibility);
    window.addEventListener('online', handleOnline);
    return () => {
      document.removeEventListener('visibilitychange', handleVisibility);
      window.removeEventListener('online', handleOnline);
    };
  }, [gameId, teamId, resyncPlayerState]);
}

export default useTeamSync;
