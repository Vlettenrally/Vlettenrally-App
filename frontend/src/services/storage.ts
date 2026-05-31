/**
 * Storage Service - Handles local storage operations
 */

import type { UserRole } from './auth';
import type { AdminTeam, PlayerTeam, Game } from '../types/entities';

/** Team data that can be stored - either full admin view or player view */
type StoredTeam = AdminTeam | PlayerTeam;

const STORAGE_KEYS = {
  AUTH_TOKEN: 'auth_token',
  REFRESH_TOKEN: 'refresh_token',
  TEAM_ID: 'team_id',
  GAME_ID: 'game_id',
  TEAM_DATA: 'team_data',
  MESSAGES: 'messages',
  PREFERENCES: 'preferences',
  USER_ROLE: 'user_role',
  USER_ROLES: 'user_roles', // For storing multiple admin roles
  SIDEBAR_COLLAPSED: 'sidebar_collapsed',
  DEVICE_ID: 'device_id',
  DEVICE_NICKNAME: 'device_nickname',
  ADMIN_DEVICE_ID: 'admin_device_id',
} as const;

class StorageService {
  // Get item from localStorage
  getItem<T>(key: string): T | null {
    try {
      const item = localStorage.getItem(key);
      return item ? JSON.parse(item) : null;
    } catch (error) {
      console.error(`Error reading from localStorage (${key}):`, error);
      return null;
    }
  }

  // Set item in localStorage
  setItem<T>(key: string, value: T): void {
    try {
      localStorage.setItem(key, JSON.stringify(value));
    } catch (error) {
      console.error(`Error writing to localStorage (${key}):`, error);
    }
  }

  // Remove item from localStorage
  removeItem(key: string): void {
    try {
      localStorage.removeItem(key);
    } catch (error) {
      console.error(`Error removing from localStorage (${key}):`, error);
    }
  }

  // Clear all items
  clear(): void {
    try {
      localStorage.clear();
    } catch (error) {
      console.error('Error clearing localStorage:', error);
    }
  }

  // Auth token (access token)
  getAuthToken(): string | null {
    return this.getItem<string>(STORAGE_KEYS.AUTH_TOKEN);
  }

  setAuthToken(token: string): void {
    this.setItem(STORAGE_KEYS.AUTH_TOKEN, token);
  }

  removeAuthToken(): void {
    this.removeItem(STORAGE_KEYS.AUTH_TOKEN);
  }

  // Refresh token
  getRefreshToken(): string | null {
    return this.getItem<string>(STORAGE_KEYS.REFRESH_TOKEN);
  }

  setRefreshToken(token: string): void {
    this.setItem(STORAGE_KEYS.REFRESH_TOKEN, token);
  }

  removeRefreshToken(): void {
    this.removeItem(STORAGE_KEYS.REFRESH_TOKEN);
  }

  // Team ID
  getTeamId(): string | null {
    return this.getItem<string>(STORAGE_KEYS.TEAM_ID);
  }

  setTeamId(teamId: string): void {
    this.setItem(STORAGE_KEYS.TEAM_ID, teamId);
  }

  removeTeamId(): void {
    this.removeItem(STORAGE_KEYS.TEAM_ID);
  }

  // Game ID
  getGameId(): string | null {
    return this.getItem<string>(STORAGE_KEYS.GAME_ID);
  }

  setGameId(gameId: string): void {
    this.setItem(STORAGE_KEYS.GAME_ID, gameId);
  }

  removeGameId(): void {
    this.removeItem(STORAGE_KEYS.GAME_ID);
  }

  // Team data
  getTeamData(): StoredTeam | null {
    return this.getItem<StoredTeam>(STORAGE_KEYS.TEAM_DATA);
  }

  setTeamData(data: StoredTeam): void {
    this.setItem<StoredTeam>(STORAGE_KEYS.TEAM_DATA, data);
    // store timestamp for cache freshness if needed
    this.setItem<number>(`${STORAGE_KEYS.TEAM_DATA}_ts`, Date.now());
  }

  removeTeamData(): void {
    this.removeItem(STORAGE_KEYS.TEAM_DATA);
    this.removeItem(`${STORAGE_KEYS.TEAM_DATA}_ts`);
  }

  // Game data (optional cached whole game)
  getGameData(): Game | null {
    return this.getItem<Game>(STORAGE_KEYS.GAME_ID /* key for id only; whole game not persisted by default */);
  }

  setGameData(game: Game): void {
    this.setItem<Game>(STORAGE_KEYS.GAME_ID, game);
    this.setItem<number>(`${STORAGE_KEYS.GAME_ID}_ts`, Date.now());
  }

  removeGameData(): void {
    this.removeItem(STORAGE_KEYS.GAME_ID);
    this.removeItem(`${STORAGE_KEYS.GAME_ID}_ts`);
  }

  // Messages (for offline support)
  getMessages(): unknown[] | null {
    return this.getItem<unknown[]>(STORAGE_KEYS.MESSAGES);
  }

  setMessages(messages: unknown[]): void {
    this.setItem(STORAGE_KEYS.MESSAGES, messages);
  }

  addMessage(message: unknown): void {
    const messages = this.getMessages() || [];
    messages.push(message);
    this.setMessages(messages);
  }

  // User preferences
  getPreferences(): Record<string, unknown> | null {
    return this.getItem<Record<string, unknown>>(STORAGE_KEYS.PREFERENCES);
  }

  setPreferences(preferences: Record<string, unknown>): void {
    this.setItem(STORAGE_KEYS.PREFERENCES, preferences);
  }

  // Check if user is checked in
  isCheckedIn(): boolean {
    return !!(this.getTeamId() && this.getGameId());
  }

  // User role
  getUserRole(): UserRole | null {
    return this.getItem<UserRole>(STORAGE_KEYS.USER_ROLE);
  }

  setUserRole(role: UserRole): void {
    this.setItem(STORAGE_KEYS.USER_ROLE, role);
  }

  removeUserRole(): void {
    this.removeItem(STORAGE_KEYS.USER_ROLE);
  }

  // User roles (for storing multiple admin roles)
  getUserRoles(): string[] | null {
    return this.getItem<string[]>(STORAGE_KEYS.USER_ROLES);
  }

  setUserRoles(roles: string[]): void {
    this.setItem(STORAGE_KEYS.USER_ROLES, roles);
  }

  removeUserRoles(): void {
    this.removeItem(STORAGE_KEYS.USER_ROLES);
  }

  // Check if current user is an admin
  isAdmin(): boolean {
    return this.getUserRole() === 'admin';
  }

  // Check if user has a specific role
  hasRole(role: string): boolean {
    const roles = this.getUserRoles();
    return roles ? roles.includes(role) : false;
  }

  // Check if user is a super admin
  isSuperAdmin(): boolean {
    return this.hasRole('super_admin');
  }

  // Device identity is server-assigned: the participant flow's register-device call
  // and the admin FCM registration both return a deviceId that we persist here and
  // send back as an idempotency HINT on subsequent calls. We never invent one
  // client-side, so a hostile client can't spawn rows under arbitrary ids.
  // This entry survives clearSession() because it identifies the physical browser
  // across logouts and re-check-ins.
  getDeviceId(): string | null {
    return this.getItem<string>(STORAGE_KEYS.DEVICE_ID);
  }

  setDeviceId(deviceId: string): void {
    this.setItem(STORAGE_KEYS.DEVICE_ID, deviceId);
  }

  getDeviceNickname(): string | null {
    return this.getItem<string>(STORAGE_KEYS.DEVICE_NICKNAME);
  }

  setDeviceNickname(nickname: string): void {
    this.setItem(STORAGE_KEYS.DEVICE_NICKNAME, nickname);
  }

  // Admin device id is a separate localStorage entry because admin and team
  // device_registrations rows have distinct primary keys (one entity may not be
  // simultaneously owned by both an admin and a team).
  getAdminDeviceId(): string | null {
    return this.getItem<string>(STORAGE_KEYS.ADMIN_DEVICE_ID);
  }

  setAdminDeviceId(deviceId: string): void {
    this.setItem(STORAGE_KEYS.ADMIN_DEVICE_ID, deviceId);
  }

  // Sidebar collapsed state (admin UI)
  getSidebarCollapsed(): boolean {
    return this.getItem<boolean>(STORAGE_KEYS.SIDEBAR_COLLAPSED) ?? false;
  }

  setSidebarCollapsed(collapsed: boolean): void {
    this.setItem(STORAGE_KEYS.SIDEBAR_COLLAPSED, collapsed);
  }

  // Clear all session data (logout)
  clearSession(): void {
    this.removeAuthToken();
    this.removeRefreshToken();
    this.removeTeamId();
    this.removeGameId();
    this.removeTeamData();
    this.removeUserRole();
    this.removeUserRoles();
  }

  // Clear cached app data but keep auth tokens (if desired)
  clearCache(): void {
    try {
      this.removeTeamId();
      this.removeGameId();
      this.removeTeamData();
      this.removeGameData();
      const messages = this.getMessages();
      if (messages) {
        this.removeItem(STORAGE_KEYS.MESSAGES);
      }
    } catch (error) {
      console.error('Error clearing cache:', error);
    }
  }
}

export const storage = new StorageService();
export default storage;
