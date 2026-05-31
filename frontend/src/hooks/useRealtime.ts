/**
 * useRealtime Hook - Handles real-time updates from WebSocket
 * 
 * Supports both team and admin connections
 */
import { useEffect } from 'react';
import { useTeamWebSocket, useAdminWebSocket } from './useWebSocket';
import teamService from '../services/teamService';
import { storage } from '../services/storage';
import { useMessageStore, type Message } from '../store/useMessageStore';
import { useTeamStore, type Achievement } from '../store/useTeamStore';
import { usePointEntryStore } from '../store/usePointEntryStore';
import type { PublicTeam, PointEntry } from '../types/entities';

interface UseRealtimeTeamOptions {
  gameId: string | null;
  teamId: string | null;
}

interface UseRealtimeAdminOptions {
  gameId: string | null;
}

/**
 * Hook for team real-time updates
 */
export function useTeamRealtime({ gameId, teamId }: UseRealtimeTeamOptions) {
  const { subscribe, connected } = useTeamWebSocket(gameId, teamId);
  const { addMessage } = useMessageStore();
  const { setRivalTeam, addAchievement, updatePoints } = useTeamStore();

  useEffect(() => {
    if (!gameId || !teamId) return;

    // Subscribe to new messages
    const unsubscribeMessages = subscribe('MESSAGE_NEW', (message) => {
      addMessage(message.payload as Message);
    });

    // Subscribe to countdown updates
    const unsubscribeCountdown = subscribe('COUNTDOWN_UPDATE', (message) => {
      console.log('Countdown update:', message.payload);
    });

    // Subscribe to team achievements
    const unsubscribeAchievements = subscribe('TEAM_ACHIEVEMENT', (message) => {
      const achievement = message.payload as Achievement & { totalPoints: number };
      addAchievement(achievement);
      updatePoints(achievement.totalPoints);
    });

    // Subscribe to rival updates
    const unsubscribeRival = subscribe('RIVAL_UPDATE', (message) => {
      // update rival immediately
      setRivalTeam(message.payload as PublicTeam);
      // fetch authoritative team data in background to refresh store
      (async () => {
        try {
          const team = await teamService.getMyTeam();
          if (team) {
            // ensure zustand store is up to date
            // updatePoints may already have been called; set full team if available
            useTeamStore.getState().setTeam(team);
          }
        } catch (e) {
          console.warn('Failed to refresh team after rival update', e);
        }
      })();
    });

    // Subscribe to ranking updates
    const unsubscribeRanking = subscribe('RANKING_UPDATE', (message) => {
      console.log('Ranking update:', message.payload);
    });

    // Subscribe to game status changes
    const unsubscribeGameStatus = subscribe('GAME_STATUS', (message) => {
      console.log('Game status changed:', message.payload);
    });

    // Admin reset of the team's check-in: drop session and bounce back to /check-in.
    const unsubscribeCheckinReset = subscribe('CHECKIN_RESET', () => {
      storage.clearSession();
      window.location.href = '/check-in';
    });

    // Admin removed a specific device: if that's us, eject. Other devices keep working.
    const unsubscribeDeviceRemoved = subscribe('CHECKIN_DEVICE_REMOVED', (message) => {
      const payload = message.payload as { deviceId?: string } | undefined;
      const localDeviceId = storage.getDeviceId();
      if (payload?.deviceId && localDeviceId && payload.deviceId === localDeviceId) {
        storage.clearSession();
        window.location.href = '/check-in';
      }
    });

    return () => {
      unsubscribeMessages();
      unsubscribeCountdown();
      unsubscribeAchievements();
      unsubscribeRival();
      unsubscribeRanking();
      unsubscribeGameStatus();
      unsubscribeCheckinReset();
      unsubscribeDeviceRemoved();
    };
  }, [gameId, teamId, subscribe, addMessage, setRivalTeam, addAchievement, updatePoints]);

  return { connected };
}

/**
 * Hook for admin real-time updates
 */
export function useAdminRealtime({ gameId }: UseRealtimeAdminOptions) {
  const { subscribe, connected } = useAdminWebSocket(gameId);
  const { addMessage } = useMessageStore();
  const { addEntry: addPointEntry, updateEntry: updatePointEntry } = usePointEntryStore();

  useEffect(() => {
    if (!gameId) return;

    // Subscribe to all messages (admins see everything)
    const unsubscribeMessages = subscribe('MESSAGE_NEW', (message) => {
      addMessage(message.payload as Message);
    });

    // Subscribe to admin-specific alerts
    const unsubscribeAlerts = subscribe('ADMIN_ALERT', (message) => {
      console.log('Admin alert:', message.payload);
    });

    // Subscribe to team check-ins
    const unsubscribeCheckins = subscribe('TEAM_CHECKIN', (message) => {
      console.log('Team checked in:', message.payload);
    });

    // Subscribe to team updates
    const unsubscribeTeamUpdates = subscribe('TEAM_UPDATE', (message) => {
      console.log('Team updated:', message.payload);
      // admin: refresh the specific team data
      (async () => {
        try {
          // payload might contain team id
          interface TeamUpdatePayload {
            id?: string;
            teamId?: string;
          }
          const payload = (message.payload || {}) as TeamUpdatePayload;
          const teamId = payload.id || payload.teamId || null;
          if (teamId && gameId) {
            const fresh = await teamService.getTeam(teamId, gameId);
            // If admin UI has a team store, it should be updated by caller; here we at least update if matches
            if (fresh) {
              // if this team is the current cached team, update storage and team store
              const current = storage.getTeamData();
              if (current && current.id === fresh.id) {
                useTeamStore.getState().setTeam(fresh);
              }
            }
          }
        } catch (e) {
          console.warn('Failed to refresh team data for admin TEAM_UPDATE', e);
        }
      })();
    });

    // Subscribe to game status changes
    const unsubscribeGameStatus = subscribe('GAME_STATUS', (message) => {
      console.log('Game status changed:', message.payload);
    });

    // Subscribe to ranking updates
    const unsubscribeRanking = subscribe('RANKING_UPDATE', (message) => {
      console.log('Ranking update:', message.payload);
    });

    // Subscribe to points-ledger events for the Points Management page
    const unsubscribePointCreated = subscribe('POINT_ENTRY_CREATED', (message) => {
      addPointEntry(message.payload as PointEntry);
    });

    const unsubscribePointUpdated = subscribe('POINT_ENTRY_UPDATED', (message) => {
      updatePointEntry(message.payload as PointEntry);
    });

    return () => {
      unsubscribeMessages();
      unsubscribeAlerts();
      unsubscribeCheckins();
      unsubscribeTeamUpdates();
      unsubscribeGameStatus();
      unsubscribeRanking();
      unsubscribePointCreated();
      unsubscribePointUpdated();
    };
  }, [gameId, subscribe, addMessage, addPointEntry, updatePointEntry]);

  return { connected };
}

/**
 * Legacy hook for backward compatibility
 * @deprecated Use useTeamRealtime or useAdminRealtime instead
 */
export function useRealtime(gameId: string | null) {
  console.warn('useRealtime is deprecated. Use useTeamRealtime or useAdminRealtime instead.');
  // This won't work properly without teamId, kept for signature compatibility
  return useTeamRealtime({ gameId, teamId: null });
}
