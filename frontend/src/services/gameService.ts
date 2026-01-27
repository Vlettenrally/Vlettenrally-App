import api from './api';
//import { storage } from './storage';
import { type Game }  from '../types/entities';

let games: Game[] = [];
let fetchGamesPromise: Promise<Game[]> | null = null;

export const gameService = {
    getGames: async (options?: { forceRefresh?: boolean }) : Promise<Game[]> => {
    const forceRefresh = options?.forceRefresh === true;

    if (!forceRefresh && games.length > 0) {
      return games;
    }

    if (fetchGamesPromise) {
      try {
        return await fetchGamesPromise;
      } catch (e) {
        fetchGamesPromise = null;
        throw e;
      }
    }

    fetchGamesPromise = (async () => {
      try {
        const response = await api.getGames();
        games = response.data ?? [];
        return games;
      } finally {
        fetchGamesPromise = null;
      }
    })();

    return fetchGamesPromise;
    },
    getGame: async (gameId: string): Promise<Game | null> => {
    return gameService.getGames().then(games => games.find((g) => g.id === gameId) || null);
  },

  getActiveGames: async (): Promise<Game[]> => {
    return (await gameService.getGames()).filter((g) => g.status === 'active');
    },
  
    getGameCount: async (): Promise<number> => {
        const games = await gameService.getGames();
        return games.length;
    }
};