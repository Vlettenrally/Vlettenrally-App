import api from './api';
import { type Game } from '../types/entities';

// Request deduplication - prevents duplicate in-flight requests
// Service worker handles caching for offline support
let fetchGamesPromise: Promise<Game[]> | null = null;

export const gameService = {
  getGames: async (): Promise<Game[]> => {
    // Deduplicate concurrent requests
    if (fetchGamesPromise) {
      return fetchGamesPromise;
    }

    fetchGamesPromise = (async () => {
      try {
        const response = await api.getGames();
        return response.data ?? [];
      } finally {
        fetchGamesPromise = null;
      }
    })();

    return fetchGamesPromise;
  },

  getGame: async (gameId: string): Promise<Game | null> => {
    const games = await gameService.getGames();
    return games.find((g) => g.id === gameId) || null;
  },

  getActiveGames: async (): Promise<Game[]> => {
    const games = await gameService.getGames();
    return games.filter((g) => g.status === 'active');
  },

  getGameCount: async (): Promise<number> => {
    const games = await gameService.getGames();
    return games.length;
  },
};