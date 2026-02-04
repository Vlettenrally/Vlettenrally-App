/**
 * Game Store - Global state for game data
 */
import { create } from 'zustand';
import { storage } from '../services/storage';
import { gameService } from '../services/gameService';
import type { Game } from '../types/entities';

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
  
  // Admin game selection
  games: Game[];
  selectedGameId: string | null;
  
  setGame: (game: Game) => void;
  setCheckpoints: (checkpoints: Checkpoint[]) => void;
  setLoading: (loading: boolean) => void;
  setError: (error: string | null) => void;
  reset: () => void;
  
  // Admin game selection actions
  setGames: (games: Game[]) => void;
  setSelectedGameId: (gameId: string | null) => void;
  fetchGames: () => Promise<void>;
  fetchGameById: (gameId: string) => Promise<void>;
}

export const useGameStore = create<GameState>((set) => ({
  game: null,
  checkpoints: [],
  isLoading: false,
  error: null,
  
  // Admin game selection
  games: [],
  selectedGameId: storage.getGameId(),

  setGame: (game) => set({ game }),
  setCheckpoints: (checkpoints) => set({ checkpoints }),
  setLoading: (isLoading) => set({ isLoading }),
  setError: (error) => set({ error }),
  reset: () => set({ game: null, checkpoints: [], isLoading: false, error: null }),
  
  // Admin game selection actions
  setGames: (games) => set({ games }),
  
  setSelectedGameId: (gameId) => {
    if (gameId) {
      storage.setGameId(gameId);
    } else {
      storage.removeGameId();
    }
    // Clear game-specific data when switching games
    set({ selectedGameId: gameId, game: null, checkpoints: [], error: null });
  },
  
  fetchGames: async () => {
    try {
      set({ isLoading: true, error: null });
      const games = await gameService.getGames();
      set({ games, isLoading: false });
    } catch (error) {
      set({ error: error instanceof Error ? error.message : 'Failed to fetch games', isLoading: false });
    }
  },
  
  fetchGameById: async (gameId: string) => {
    try {
      set({ isLoading: true, error: null });
      const game = await gameService.getGame(gameId);
      set({ game, isLoading: false });
    } catch (error) {
      set({ error: error instanceof Error ? error.message : 'Failed to fetch game', isLoading: false });
    }
  },
}));
