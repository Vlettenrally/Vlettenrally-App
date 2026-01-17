/**
 * Game Store - Global state for game data
 */
import { create } from 'zustand';

export interface Game {
  id: string;
  name: string;
  startTime: string;
  endTime: string;
  startingAreaActiveTime?: string;
  startSignalTime?: string;
  status: 'planned' | 'active' | 'completed';
}

export interface Checkpoint {
  id: string;
  gameId: string;
  name: string;
  openingTime: string;
  closingTime?: string;
  points: number;
  latitude?: number;
  longitude?: number;
}

interface GameState {
  game: Game | null;
  checkpoints: Checkpoint[];
  isLoading: boolean;
  error: string | null;
  
  setGame: (game: Game) => void;
  setCheckpoints: (checkpoints: Checkpoint[]) => void;
  setLoading: (loading: boolean) => void;
  setError: (error: string | null) => void;
  reset: () => void;
}

export const useGameStore = create<GameState>((set) => ({
  game: null,
  checkpoints: [],
  isLoading: false,
  error: null,

  setGame: (game) => set({ game }),
  setCheckpoints: (checkpoints) => set({ checkpoints }),
  setLoading: (isLoading) => set({ isLoading }),
  setError: (error) => set({ error }),
  reset: () => set({ game: null, checkpoints: [], isLoading: false, error: null }),
}));
