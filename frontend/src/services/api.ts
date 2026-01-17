/**
 * API Service - Handles all HTTP requests to the backend
 * 
 * When VITE_USE_MOCKS=true, this service automatically delegates
 * to the mock API for development without a backend.
 */

import { config } from '../config';

const API_BASE_URL = config.apiBaseUrl;

interface ApiResponse<T> {
  data?: T;
  error?: string;
  status: number;
}

class ApiService {
  private async request<T>(
    endpoint: string,
    options: RequestInit = {}
  ): Promise<ApiResponse<T>> {
    const token = localStorage.getItem('auth_token');
    
    const headers: HeadersInit = {
      'Content-Type': 'application/json',
      ...(token && { Authorization: `Bearer ${token}` }),
      ...options.headers,
    };

    try {
      const response = await fetch(`${API_BASE_URL}${endpoint}`, {
        ...options,
        headers,
      });

      const data = await response.json().catch(() => null);

      if (!response.ok) {
        return {
          error: data?.message || 'Request failed',
          status: response.status,
        };
      }

      return {
        data,
        status: response.status,
      };
    } catch (error) {
      console.error('API request failed:', error);
      return {
        error: error instanceof Error ? error.message : 'Network error',
        status: 0,
      };
    }
  }

  // GET request
  async get<T>(endpoint: string): Promise<ApiResponse<T>> {
    return this.request<T>(endpoint, { method: 'GET' });
  }

  // POST request
  async post<T>(endpoint: string, data?: unknown): Promise<ApiResponse<T>> {
    return this.request<T>(endpoint, {
      method: 'POST',
      body: JSON.stringify(data),
    });
  }

  // PUT request
  async put<T>(endpoint: string, data?: unknown): Promise<ApiResponse<T>> {
    return this.request<T>(endpoint, {
      method: 'PUT',
      body: JSON.stringify(data),
    });
  }

  // DELETE request
  async delete<T>(endpoint: string): Promise<ApiResponse<T>> {
    return this.request<T>(endpoint, { method: 'DELETE' });
  }

  // Authentication endpoints
  async exchangeToken(registrationToken: string) {
    return this.post('/auth/token', { registrationToken });
  }

  async refreshAccessToken(refreshToken: string) {
    return this.post('/auth/refresh', { refreshToken });
  }

  // Team endpoints
  async getMyTeam() {
    return this.get('/team/me');
  }

  async getTeam(teamId: string) {
    return this.get(`/teams/${teamId}`);
  }

  async getRivalTeam(teamId: string) {
    return this.get(`/teams/${teamId}/rival`);
  }

  // Game endpoints
  async getGame(gameId: string) {
    return this.get(`/games/${gameId}`);
  }

  async getActiveGames() {
    return this.get('/games/active');
  }

  // Messages endpoints
  async getMessages(teamId: string) {
    return this.get(`/messages/team/${teamId}`);
  }

  async markMessageAsRead(messageId: string) {
    return this.put(`/messages/${messageId}/read`, {});
  }

  // Rankings endpoints
  async getRankings(gameId: string) {
    return this.get(`/games/${gameId}/rankings`);
  }

  // Achievements endpoints
  async getTeamAchievements(teamId: string) {
    return this.get(`/teams/${teamId}/achievements`);
  }
}

// Export either the real API or mock API based on configuration
const api = new ApiService();
export default api;
