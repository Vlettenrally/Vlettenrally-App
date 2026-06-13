/**
 * Team Store - Global state for team data
 */
import { create } from 'zustand';
import { type PlayerTeam, type PublicTeam, type RankingEntry } from '../types/entities';

export type { RankingEntry };

export interface Achievement {
  id: string;
  teamId: string;
  checkpointId: string;
  pointsEarned: number;
  achievedAt: string;
}

interface TeamState {
  team: PlayerTeam | null;
  rivalTeams: PublicTeam[];
  rankings: RankingEntry[];
  achievements: Achievement[];
  isLoading: boolean;
  error: string | null;

  setTeam: (team: PlayerTeam | null) => void;
  setRivalTeams: (rivalTeams: PublicTeam[]) => void;
  setRankings: (rankings: RankingEntry[]) => void;
  setAchievements: (achievements: Achievement[]) => void;
  addAchievement: (achievement: Achievement) => void;
  updatePoints: (points: number) => void;
  setLoading: (loading: boolean) => void;
  setError: (error: string | null) => void;
  reset: () => void;
}

export const useTeamStore = create<TeamState>((set) => ({
  team: null,
  rivalTeams: [],
  rankings: [],
  achievements: [],
  isLoading: false,
  error: null,

  setTeam: (team) => set({ team }),
  setRivalTeams: (rivalTeams) => set({ rivalTeams }),
  setRankings: (rankings) => set({ rankings }),
  setAchievements: (achievements) => set({ achievements }),
  addAchievement: (achievement) =>
    set((state) => {
      // Dedupe by id: the counter is hydrated on load and also receives
      // TEAM_ACHIEVEMENT WS events, which can race and double-count.
      if (state.achievements.some((a) => a.id === achievement.id)) {
        return state;
      }
      return { achievements: [...state.achievements, achievement] };
    }),
  updatePoints: (points) =>
    set((state) => ({
      team: state.team ? { ...state.team, totalPoints: points } : null,
    })),
  setLoading: (isLoading) => set({ isLoading }),
  setError: (error) => set({ error }),
  reset: () =>
    set({
      team: null,
      rivalTeams: [],
      rankings: [],
      achievements: [],
      isLoading: false,
      error: null,
    }),
}));
