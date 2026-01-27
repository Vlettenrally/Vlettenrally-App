/**
 * Team Store - Global state for team data
 */
import { create } from 'zustand';
import { type PlayerTeam, type PublicTeam } from '../types/entities';

export interface Achievement {
  id: string;
  teamId: string;
  checkpointId: string;
  pointsEarned: number;
  achievedAt: string;
}

interface TeamState {
  team: PlayerTeam | null;
  rivalTeam: PublicTeam | null;
  achievements: Achievement[];
  isLoading: boolean;
  error: string | null;
  
  setTeam: (team: PlayerTeam | null) => void;
  setRivalTeam: (rivalTeam: PublicTeam | null) => void;
  setAchievements: (achievements: Achievement[]) => void;
  addAchievement: (achievement: Achievement) => void;
  updatePoints: (points: number) => void;
  setLoading: (loading: boolean) => void;
  setError: (error: string | null) => void;
  reset: () => void;
}

export const useTeamStore = create<TeamState>((set) => ({
  team: null,
  rivalTeam: null,
  achievements: [],
  isLoading: false,
  error: null,

  setTeam: (team) => set({ team }),
  setRivalTeam: (rivalTeam) => set({ rivalTeam }),
  setAchievements: (achievements) => set({ achievements }),
  addAchievement: (achievement) =>
    set((state) => ({
      achievements: [...state.achievements, achievement],
    })),
  updatePoints: (points) =>
    set((state) => ({
      team: state.team ? { ...state.team, totalPoints: points } : null,
    })),
  setLoading: (isLoading) => set({ isLoading }),
  setError: (error) => set({ error }),
  reset: () =>
    set({
      team: null,
      rivalTeam: null,
      achievements: [],
      isLoading: false,
      error: null,
    }),
}));
