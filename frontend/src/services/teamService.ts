import api from "./api";
import { storage } from './storage';
import type { AdminTeam, PlayerTeam, PublicTeam } from '../types/entities';

let teams: AdminTeam[] = [];
let myTeam: PlayerTeam | null = null;
let fetchMyTeamPromise: Promise<PlayerTeam | null> | null = null;
let fetchTeamsPromise: Promise<AdminTeam[] | null> | null = null;

export const teamService = {
  getMyTeam: async (options?: { forceRefresh?: boolean }) : Promise<PlayerTeam | null> => {
    const forceRefresh = options?.forceRefresh === true;

    if (!forceRefresh && myTeam !== null) {
      return myTeam;
    }

    if (fetchMyTeamPromise) {
      try {
        return await fetchMyTeamPromise;
      } catch (e) {
        fetchMyTeamPromise = null;
        throw e;
      }
    }

    fetchMyTeamPromise = (async () => {
      try {
        const response = await api.getMyTeam();
        if (response && response.data) {
          const team = response.data as PlayerTeam;
          try {
            myTeam = team;
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

  getTeam: async (teamId: string, options?: { forceRefresh?: boolean, gameId?: string }): Promise<AdminTeam | null> => {
    const forceRefresh = options?.forceRefresh === true;
    const gameId = options?.gameId;

    if (!forceRefresh) {
      const cached = teams.find((t) => t.id === teamId);
      if (cached) {
        return cached;
      }
    }

    if (!gameId) {
      throw new Error("gameId is required to fetch teams");
    }

    if (!fetchTeamsPromise) {
      fetchTeamsPromise = (async () => {
        try {
          const response = await api.getTeamsByGame(gameId);
          if (response && response.data) {
            // Add each team individually to the teams array
            response.data.forEach((team: AdminTeam) => {
              if (!teams.find((t) => t.id === team.id)) {
                teams.push(team);
              }
            });
          }
          return response.data ?? [];
        } finally {
          fetchTeamsPromise = null;
        }
      })();
    }

    await fetchTeamsPromise;
    return teams.find((t) => t.id === teamId) || null;
  },

  getTeamsByGame: async (gameId: string) : Promise<AdminTeam[]> => {
    const forceRefresh = false;

    if (!forceRefresh && teams.length > 0) {
      const cachedTeams = teams;
      if (cachedTeams.length > 0) {
        return cachedTeams;
      }
    }

    if (!fetchTeamsPromise) {
      fetchTeamsPromise = (async () => {
        try {
          const response = await api.getTeamsByGame(gameId);
          if (response && response.data) {
            teams = response.data as AdminTeam[];
          }
          return teams;
        } finally {
          fetchTeamsPromise = null;
        }
      })();
    }

    await fetchTeamsPromise;
    return teams;
  },

  getRivalTeams: async (teamId: string): Promise<PublicTeam[]> => {
    const response = await api.getRivalTeams(teamId);
    return response.data ?? [];
  },

  updateTeam: async (teamId: string, data: Partial<AdminTeam>): Promise<AdminTeam | null> => {
    const response = await api.updateTeam(teamId, data);
    if (response.data) {
      // Update cached team if it exists
      const index = teams.findIndex((t) => t.id === teamId);
      if (index !== -1) {
        teams[index] = response.data;
      }
    }
    return response.data ?? null;
  },

  createTeam: async (data: Partial<AdminTeam> & { gameId: string }): Promise<AdminTeam | null> => {
    const response = await api.createTeam(data);
    if (response.data) {
      // Add new team to cache
      teams.push(response.data);
    }
    return response.data ?? null;
  },

  clearCache: () => {
    teams = [];
    myTeam = null;
    fetchMyTeamPromise = null;
    fetchTeamsPromise = null;
  },
};

export default teamService;