/**
 * API Service - Handles all HTTP requests to the backend
 * 
 * When VITE_USE_MOCKS=true, this service automatically delegates
 * to the mock API for development without a backend.
 */

import { config } from '../config';
import type { 
  Game, 
  AdminTeam, 
  PlayerTeam, 
  PublicTeam, 
  AdminMessage,
  AdminMessageListResponse,
  MessageTemplate,
  SendAdminMessageRequest,
  MessageTemplateRequest,
  RetryMessageResponse 
} from '../types/entities';
import storage from './storage';
import { isTokenExpiringSoon } from './auth';

const API_BASE_URL = config.apiBaseUrl;

interface ApiResponse<T> {
  data?: T;
  error?: string;
  status: number;
}

class ApiService {
  private isRefreshing = false;
  private refreshPromise: Promise<boolean> | null = null;

  /**
   * Refresh token if needed (proactive refresh)
   * Returns true if token is valid/refreshed, false if refresh failed
   */
  private async refreshTokenIfNeeded(): Promise<boolean> {
    const token = storage.getAuthToken();
    const refreshToken = storage.getRefreshToken();

    // No tokens, can't refresh
    if (!token || !refreshToken) {
      return false;
    }

    // Token not expiring soon, no need to refresh
    if (!isTokenExpiringSoon(token)) {
      return true;
    }

    // If already refreshing, wait for the existing refresh to complete
    if (this.isRefreshing && this.refreshPromise) {
      return this.refreshPromise;
    }

    // Start refresh process
    this.isRefreshing = true;
    this.refreshPromise = (async () => {
      try {
        console.log('[API] Token expiring soon, refreshing...');
        const response = await fetch(`${API_BASE_URL}/auth/refresh`, {
          method: 'POST',
          headers: { 'Content-Type': 'application/json' },
          body: JSON.stringify({ refreshToken }),
        });

        if (!response.ok) {
          console.error('[API] Token refresh failed:', response.status);
          return false;
        }

        const data = await response.json();
        
        // Update stored tokens
        storage.setAuthToken(data.accessToken);
        if (data.refreshToken) {
          storage.setRefreshToken(data.refreshToken);
        }

        console.log('[API] Token refreshed successfully');
        return true;
      } catch (error) {
        console.error('[API] Token refresh error:', error);
        return false;
      } finally {
        this.isRefreshing = false;
        this.refreshPromise = null;
      }
    })();

    return this.refreshPromise;
  }

  private async request<T>(
    endpoint: string,
    options: RequestInit = {}
  ): Promise<ApiResponse<T>> {
    // Proactively refresh token if expiring soon (but not for auth endpoints)
    if (!endpoint.startsWith('/auth/')) {
      const refreshed = await this.refreshTokenIfNeeded();
      if (!refreshed) {
        // Token refresh failed, but continue with request (might still work or trigger 401)
        console.warn('[API] Token refresh failed, continuing with request');
      }
    }

    const token = storage.getAuthToken();
    
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

      // Handle 401 Unauthorized - attempt token refresh and retry once
      if (response.status === 401 && !endpoint.startsWith('/auth/')) {
        console.log('[API] Got 401, attempting token refresh...');
        
        const refreshToken = storage.getRefreshToken();
        if (!refreshToken) {
          console.error('[API] No refresh token available, redirecting to login');
          this.redirectToLogin();
          return {
            error: 'Authentication failed',
            status: 401,
          };
        }

        // Attempt to refresh token
        try {
          const refreshResponse = await fetch(`${API_BASE_URL}/auth/refresh`, {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify({ refreshToken }),
          });

          if (!refreshResponse.ok) {
            console.error('[API] Token refresh failed, redirecting to login');
            this.redirectToLogin();
            return {
              error: 'Session expired',
              status: 401,
            };
          }

          const refreshData = await refreshResponse.json();
          
          // Update stored tokens
          storage.setAuthToken(refreshData.accessToken);
          if (refreshData.refreshToken) {
            storage.setRefreshToken(refreshData.refreshToken);
          }

          console.log('[API] Token refreshed, retrying original request');

          // Retry original request with new token
          const newHeaders: HeadersInit = {
            'Content-Type': 'application/json',
            Authorization: `Bearer ${refreshData.accessToken}`,
            ...options.headers,
          };

          const retryResponse = await fetch(`${API_BASE_URL}${endpoint}`, {
            ...options,
            headers: newHeaders,
          });

          const retryData = await retryResponse.json().catch(() => null);

          if (!retryResponse.ok) {
            return {
              error: retryData?.message || 'Request failed',
              status: retryResponse.status,
            };
          }

          return {
            data: retryData,
            status: retryResponse.status,
          };
        } catch (refreshError) {
          console.error('[API] Token refresh error:', refreshError);
          this.redirectToLogin();
          return {
            error: 'Session expired',
            status: 401,
          };
        }
      }

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

  /**
   * Redirect to appropriate login page based on user role
   */
  private redirectToLogin(): void {
    const role = storage.getUserRole();
    storage.clearSession();
    
    if (role === 'admin') {
      window.location.href = '/admin/login';
    } else {
      window.location.href = '/check-in';
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

  async logout() {
    return this.post('/auth/logout');
  }

  // Team endpoints
  async getMyTeam() : Promise<ApiResponse<PlayerTeam>> {
    return this.get('/team/me');
  }

  async getTeam(teamId: string) : Promise<ApiResponse<AdminTeam>> {
    return this.get(`/teams/${teamId}`);
  }

  async getTeamsByGame(gameId: string): Promise<ApiResponse<AdminTeam[]>> {
    if (!gameId) {
      return { data: [], status: 200 };
    }
    return this.get(`/games/${gameId}/teams`);
  }

  async getRivalTeams(teamId: string) : Promise<ApiResponse<PublicTeam[]>> {
    return this.get(`/teams/${teamId}/rivals`);
  }

  async updateTeam(teamId: string, data: Partial<AdminTeam>) : Promise<ApiResponse<AdminTeam>> {
    return this.put(`/teams/${teamId}`, data);
  }

  async createTeam(data: Partial<AdminTeam> & { gameId: string }) : Promise<ApiResponse<AdminTeam>> {
    return this.post('/teams', {
      gameId: data.gameId,
      name: data.name,
      description: data.description,
      boatNumber: data.boatNumber,
      participants: data.participants,
      notes: data.notes,
    });
  }

  // Game endpoints
async getGames() : Promise<ApiResponse<Game[]>> {
    return this.get('/games');
  }
  
  async getGame(gameId: string) : Promise<ApiResponse<Game>> {
    return this.get(`/games/${gameId}`);
  }

  async getActiveGames() : Promise<ApiResponse<Game[]>> {
    return this.get('/games/active');
  }

  async getGameCount() : Promise<ApiResponse<number>> {
    return this.get('/games/count');
  }

  // Messages endpoints
  async getMessages(teamId: string) {
    return this.get(`/messages/team/${teamId}`);
  }

  async getMessage(messageId: string) {
    return this.get(`/messages/${messageId}`);
  }

  async markMessageAsRead(messageId: string) {
    return this.put(`/messages/${messageId}/read`, {});
  }

  async markMessagesAsRead(messageIds: string[]): Promise<ApiResponse<{ success: boolean }>> {
    return this.post('/messages/mark-read', { ids: messageIds });
  }

  // Admin Messages endpoints
  async sendAdminMessage(request: SendAdminMessageRequest): Promise<ApiResponse<AdminMessage>> {
    return this.post('/messages/admin', request);
  }

  async getAdminMessagesByGame(
    gameId: string, 
    page: number = 0, 
    pageSize: number = 20
  ): Promise<ApiResponse<AdminMessageListResponse>> {
    return this.get(`/messages/admin/games/${gameId}?page=${page}&pageSize=${pageSize}`);
  }

  async retryFailedDeliveries(messageId: string): Promise<ApiResponse<RetryMessageResponse>> {
    return this.post(`/messages/admin/${messageId}/retry`);
  }

  async retryAllFailedDeliveries(gameId: string): Promise<ApiResponse<RetryMessageResponse>> {
    return this.post(`/messages/admin/games/${gameId}/retry-all-failed`);
  }

  // Message Template endpoints
  async getMessageTemplates(): Promise<ApiResponse<MessageTemplate[]>> {
    return this.get('/messages/admin/templates');
  }

  async createMessageTemplate(request: MessageTemplateRequest): Promise<ApiResponse<MessageTemplate>> {
    return this.post('/messages/admin/templates', request);
  }

  async updateMessageTemplate(templateId: string, request: MessageTemplateRequest): Promise<ApiResponse<MessageTemplate>> {
    return this.put(`/messages/admin/templates/${templateId}`, request);
  }

  async deleteMessageTemplate(templateId: string): Promise<ApiResponse<void>> {
    return this.delete(`/messages/admin/templates/${templateId}`);
  }

  // Rankings endpoints
  async getRankings(gameId: string) {
    return this.get(`/games/${gameId}/rankings`);
  }

  // Achievements endpoints
  async getTeamAchievements(teamId: string) {
    return this.get(`/teams/${teamId}/achievements`);
  }

  // FCM Token registration
  async registerFcmToken(token: string): Promise<ApiResponse<void>> {
    return this.post('/fcm-tokens', { token });
  }

  async unregisterFcmToken(token: string): Promise<ApiResponse<void>> {
    return this.delete(`/fcm-tokens/${encodeURIComponent(token)}`);
  }
}

// Export either the real API or mock API based on configuration
const api = new ApiService();
export default api;
