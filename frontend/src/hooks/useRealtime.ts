/**
 * useRealtime Hook - Handles real-time updates from WebSocket
 * 
 * Supports both team and admin connections
 */
import { useEffect } from 'react';
import { useTeamWebSocket, useAdminWebSocket } from './useWebSocket';
import { useMessageStore, type Message } from '../store/useMessageStore';
import { useTeamStore, type Team, type Achievement } from '../store/useTeamStore';

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
      setRivalTeam(message.payload as Team);
    });

    // Subscribe to ranking updates
    const unsubscribeRanking = subscribe('RANKING_UPDATE', (message) => {
      console.log('Ranking update:', message.payload);
    });

    // Subscribe to game status changes
    const unsubscribeGameStatus = subscribe('GAME_STATUS', (message) => {
      console.log('Game status changed:', message.payload);
    });

    return () => {
      unsubscribeMessages();
      unsubscribeCountdown();
      unsubscribeAchievements();
      unsubscribeRival();
      unsubscribeRanking();
      unsubscribeGameStatus();
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
    });

    // Subscribe to game status changes
    const unsubscribeGameStatus = subscribe('GAME_STATUS', (message) => {
      console.log('Game status changed:', message.payload);
    });

    // Subscribe to ranking updates
    const unsubscribeRanking = subscribe('RANKING_UPDATE', (message) => {
      console.log('Ranking update:', message.payload);
    });

    return () => {
      unsubscribeMessages();
      unsubscribeAlerts();
      unsubscribeCheckins();
      unsubscribeTeamUpdates();
      unsubscribeGameStatus();
      unsubscribeRanking();
    };
  }, [gameId, subscribe, addMessage]);

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
