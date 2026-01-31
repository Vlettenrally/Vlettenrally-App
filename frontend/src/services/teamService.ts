import api from "./api";
import { storage } from './storage';
import type { AdminTeam, PlayerTeam, PublicTeam } from '../types/entities';

// Request deduplication - prevents duplicate in-flight requests
// Service worker handles caching for offline support
let fetchMyTeamPromise: Promise<PlayerTeam | null> | null = null;
const fetchTeamsPromises = new Map<string, Promise<AdminTeam[]>>();

export const teamService = {
  getMyTeam: async (): Promise<PlayerTeam | null> => {
    // Deduplicate concurrent requests
    if (fetchMyTeamPromise) {
      return fetchMyTeamPromise;
    }

    fetchMyTeamPromise = (async () => {
      try {
        const response = await api.getMyTeam();
        if (response?.data) {
          const team = response.data as PlayerTeam;
          try {
            storage.setTeamId(team.id);
            storage.setGameId(team.gameId);
          } catch (e) {
            console.warn('Failed to persist team data to storage', e);
          }
        }
        return response.data ?? null;
      } finally {
        fetchMyTeamPromise = null;
      }
    })();

    return fetchMyTeamPromise;
  },

  getTeam: async (teamId: string, gameId: string): Promise<AdminTeam | null> => {
    const teams = await teamService.getTeamsByGame(gameId);
    return teams.find((t) => t.id === teamId) || null;
  },

  getTeamsByGame: async (gameId: string): Promise<AdminTeam[]> => {
    // Check for existing in-flight request for this specific game
    const existingPromise = fetchTeamsPromises.get(gameId);
    if (existingPromise) {
      return existingPromise;
    }

    const promise = (async () => {
      try {
        const response = await api.getTeamsByGame(gameId);
        return (response.data as AdminTeam[]) ?? [];
      } finally {
        fetchTeamsPromises.delete(gameId);
      }
    })();

    fetchTeamsPromises.set(gameId, promise);
    return promise;
  },

  getRivalTeams: async (teamId: string): Promise<PublicTeam[]> => {
    const response = await api.getRivalTeams(teamId);
    return response.data ?? [];
  },

  updateTeam: async (teamId: string, data: Partial<AdminTeam>): Promise<AdminTeam | null> => {
    const response = await api.updateTeam(teamId, data);
    return response.data ?? null;
  },

  createTeam: async (data: Partial<AdminTeam> & { gameId: string }): Promise<AdminTeam | null> => {
    const response = await api.createTeam(data);
    return response.data ?? null;
  },
};

export default teamService;