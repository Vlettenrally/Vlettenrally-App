/**
 * Storage Service - Handles local storage operations
 */

const STORAGE_KEYS = {
  AUTH_TOKEN: 'auth_token',
  REFRESH_TOKEN: 'refresh_token',
  TEAM_ID: 'team_id',
  GAME_ID: 'game_id',
  TEAM_DATA: 'team_data',
  MESSAGES: 'messages',
  PREFERENCES: 'preferences',
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
  getTeamData(): unknown | null {
    return this.getItem(STORAGE_KEYS.TEAM_DATA);
  }

  setTeamData(data: unknown): void {
    this.setItem(STORAGE_KEYS.TEAM_DATA, data);
  }

  removeTeamData(): void {
    this.removeItem(STORAGE_KEYS.TEAM_DATA);
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

  // Clear all session data (logout)
  clearSession(): void {
    this.removeAuthToken();
    this.removeRefreshToken();
    this.removeTeamId();
    this.removeGameId();
    this.removeTeamData();
  }
}

export const storage = new StorageService();
export default storage;
