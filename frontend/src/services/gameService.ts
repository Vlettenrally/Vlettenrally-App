import api from './api';
import { type Game, type GameRequest, type ExternalEvent, type TeamImportResult, type TrackerAssignResult, type LocationTestResult, type BackfillResult } from '../types/entities';

export type BackfillOutcome =
  | { status: 'ok'; result: BackfillResult }
  | { status: 'cooldown'; error?: string }
  | { status: 'error'; error?: string };

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
    const response = await api.getGame(gameId);
    return response.data ?? null;
  },

  getActiveGames: async (): Promise<Game[]> => {
    const response = await api.getActiveGames();
    return response.data ?? [];
  },

  getGameCount: async (): Promise<number> => {
    const response = await api.getGameCount();
    return response.data ?? 0;
  },

  createGame: async (data: GameRequest): Promise<Game> => {
    const response = await api.createGame(data);
    if (response.error) {
      throw new Error(response.error);
    }
    return response.data!;
  },

  updateGame: async (gameId: string, data: GameRequest): Promise<Game> => {
    const response = await api.updateGame(gameId, data);
    if (response.error) {
      throw new Error(response.error);
    }
    return response.data!;
  },

  deleteGame: async (gameId: string): Promise<void> => {
    const response = await api.deleteGame(gameId);
    if (response.error) {
      throw new Error(response.error);
    }
  },

  importTeams: async (gameId: string): Promise<TeamImportResult> => {
    const response = await api.importTeams(gameId);
    if (response.error) {
      throw new Error(response.error);
    }
    return response.data!;
  },

  assignTrackers: async (gameId: string): Promise<TrackerAssignResult> => {
    const response = await api.assignTrackers(gameId);
    if (response.error) {
      throw new Error(response.error);
    }
    return response.data!;
  },

  getAvailableTrackers: async (gameId: string): Promise<string[]> => {
    const response = await api.getAvailableTrackers(gameId);
    return response.data ?? [];
  },

  exportTrackers: async (gameId: string): Promise<string> => {
    const { csv, error } = await api.exportTrackers(gameId);
    if (error || csv === undefined) {
      throw new Error(error ?? 'Export mislukt');
    }
    return csv;
  },

  testLocationEndpoint: async (gameId: string): Promise<LocationTestResult> => {
    const response = await api.testLocationEndpoint(gameId);
    if (response.error) {
      throw new Error(response.error);
    }
    return response.data!;
  },

  // Backfill a team's historical track. Returns a discriminated outcome so callers can tell a
  // cooldown (HTTP 429) apart from a genuine failure without catching exceptions.
  backfillTeamTrack: async (gameId: string, teamId: string): Promise<BackfillOutcome> => {
    const response = await api.backfillTeamTrack(gameId, teamId);
    if (response.status === 429) {
      return { status: 'cooldown', error: response.error };
    }
    if (response.error || !response.data) {
      return { status: 'error', error: response.error };
    }
    return { status: 'ok', result: response.data };
  },

  getExternalEvents: async (): Promise<ExternalEvent[]> => {
    const response = await api.getExternalEvents();
    return response.data ?? [];
  },
};