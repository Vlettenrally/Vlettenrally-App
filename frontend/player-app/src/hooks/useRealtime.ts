/**
 * useRealtime Hook - Handles real-time updates from WebSocket
 */
import { useEffect } from 'react';
import { useWebSocket } from './useWebSocket';
import { useMessageStore, type Message } from '../store/useMessageStore';
import { useTeamStore, type Team, type Achievement } from '../store/useTeamStore';

export function useRealtime(gameId: string | null) {
  const { subscribe } = useWebSocket(gameId);
  const { addMessage } = useMessageStore();
  const { setRivalTeam, addAchievement, updatePoints } = useTeamStore();

  useEffect(() => {
    if (!gameId) return;

    // Subscribe to new messages
    const unsubscribeMessages = subscribe('MESSAGE_NEW', (message) => {
      addMessage(message.payload as Message);
    });

    // Subscribe to countdown updates
    const unsubscribeCountdown = subscribe('COUNTDOWN_UPDATE', (message) => {
      // Handle countdown update (could trigger a notification or update UI)
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
      // Update rankings in state if needed
    });

    // Subscribe to game status changes
    const unsubscribeGameStatus = subscribe('GAME_STATUS', (message) => {
      console.log('Game status changed:', message.payload);
      // Update game status in state
    });

    // Cleanup subscriptions
    return () => {
      unsubscribeMessages();
      unsubscribeCountdown();
      unsubscribeAchievements();
      unsubscribeRival();
      unsubscribeRanking();
      unsubscribeGameStatus();
    };
  }, [gameId, subscribe, addMessage, setRivalTeam, addAchievement, updatePoints]);
}
