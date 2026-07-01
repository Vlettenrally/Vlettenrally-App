import api from "./api";
import { storage } from './storage';
import type { AdminTeam, CheckInDevice, PlayerTeam, PublicTeam, TeamOption } from '../types/entities';

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

  setRivalTeams: async (teamId: string, rivalTeamIds: string[]): Promise<{ rivals: PublicTeam[]; error: string | null }> => {
    const response = await api.setRivalTeams(teamId, rivalTeamIds);
    return { rivals: response.data ?? [], error: response.error ?? null };
  },

  // Candidate list for the rival picker. Reuses the public /check-in/games/{id}/teams
  // endpoint which only returns id, name, checkedIn, hasMainDevice — no sensitive fields.
  getEligibleRivalTeams: async (gameId: string, excludeTeamId?: string): Promise<TeamOption[]> => {
    const response = await api.getCheckInTeams(gameId);
    const teams = response.data ?? [];
    return excludeTeamId ? teams.filter((t) => t.id !== excludeTeamId) : teams;
  },

  updateTeam: async (teamId: string, data: Partial<AdminTeam>): Promise<AdminTeam | null> => {
    const response = await api.updateTeam(teamId, data);
    return response.data ?? null;
  },

  assignTracker: async (teamId: string, trackerId: string | null): Promise<{ team: AdminTeam | null; error: string | null }> => {
    const response = await api.assignTeamTracker(teamId, trackerId);
    return { team: response.data ?? null, error: response.error ?? null };
  },

  updateEmergencyPhones: async (teamId: string, phoneNumbers: string[]): Promise<AdminTeam | null> => {
    const response = await api.updateEmergencyPhones(teamId, phoneNumbers);
    return response.data ?? null;
  },

  updateParticipants: async (teamId: string, participants: string[]): Promise<AdminTeam | null> => {
    const response = await api.updateParticipants(teamId, participants);
    return response.data ?? null;
  },

  checkIn: async (teamId: string): Promise<AdminTeam | null> => {
    const response = await api.checkInTeam(teamId);
    return response.data ?? null;
  },

  createTeam: async (data: Partial<AdminTeam> & { gameId: string }): Promise<AdminTeam | null> => {
    const response = await api.createTeam(data);
    return response.data ?? null;
  },

  deleteTeam: async (teamId: string): Promise<{ error: string | null }> => {
    const response = await api.deleteTeam(teamId);
    return { error: response.error ?? null };
  },

  listDevices: async (teamId: string): Promise<CheckInDevice[]> => {
    const response = await api.getTeamDevices(teamId);
    return response.data ?? [];
  },

  setMainDevice: async (teamId: string, deviceId: string): Promise<boolean> => {
    const response = await api.setMainTeamDevice(teamId, deviceId);
    return !response.error;
  },

  resetDevices: async (teamId: string): Promise<boolean> => {
    const response = await api.resetTeamDevices(teamId);
    return !response.error;
  },

  removeDevice: async (teamId: string, deviceId: string): Promise<boolean> => {
    const response = await api.removeTeamDevice(teamId, deviceId);
    return !response.error;
  },

  resetPointsLedger: async (teamId: string, gameId: string): Promise<{ error: string | null }> => {
    const response = await api.resetTeamPointsLedger(teamId, gameId);
    return { error: response.error ?? null };
  },
};

export default teamService;