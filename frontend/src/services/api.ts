/**
 * API Service - Handles all HTTP requests to the backend
 *
 * When VITE_USE_MOCKS=true, this service automatically delegates
 * to the mock API for development without a backend.
 */

import { config } from '../config';
import type {
  Game,
  GameRequest,
  ExternalEvent,
  TeamImportResult,
  LocationTestResult,
  AdminTeam,
  PlayerTeam,
  PublicTeam,
  AdminMessage,
  AdminMessageListResponse,
  MessageTemplate,
  SendAdminMessageRequest,
  MessageTemplateRequest,
  RetryMessageResponse,
  AdminUser,
  CreateAdminRequest,
  UpdateAdminRolesRequest,
  ResetPasswordResponse
} from '../types/entities';
import storage from './storage';
import { isTokenExpiringSoon } from './auth';

const API_BASE_URL = config.apiBaseUrl;

interface ApiResponse<T> {
  data?: T;
  error?: string;
  status: number;
}

interface RefreshResult {
  success: boolean;
  accessToken?: string;
  refreshToken?: string;
}

class ApiService {
  private isRefreshing = false;
  private refreshPromise: Promise<RefreshResult> | null = null;
  private cachedAccessToken: string | null = null;
  private cachedRefreshToken: string | null = null;

  /**
   * Refresh token if needed (proactive refresh)
   * Returns refresh result with new tokens if successful
   */
  private async refreshTokenIfNeeded(): Promise<RefreshResult> {
    const token = storage.getAuthToken();
    const refreshToken = storage.getRefreshToken();

    // No tokens, can't refresh
    if (!token || !refreshToken) {
      return { success: false };
    }

    // Token not expiring soon, no need to refresh
    if (!isTokenExpiringSoon(token)) {
      return { success: true };
    }

    // If already refreshing, wait for the existing refresh to complete
    if (this.isRefreshing && this.refreshPromise) {
      console.log('[API] Refresh already in progress, waiting...');
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
          return { success: false };
        }

        const data = await response.json();

        // Cache tokens in memory for concurrent requests
        this.cachedAccessToken = data.accessToken;
        this.cachedRefreshToken = data.refreshToken || null;

        // Update stored tokens
        storage.setAuthToken(data.accessToken);
        if (data.refreshToken) {
          storage.setRefreshToken(data.refreshToken);
        }

        console.log('[API] Token refreshed successfully');
        return {
          success: true,
          accessToken: data.accessToken,
          refreshToken: data.refreshToken
        };
      } catch (error) {
        console.error('[API] Token refresh error:', error);
        return { success: false };
      } finally {
        this.isRefreshing = false;
        this.refreshPromise = null;
        // Clear cached tokens after a short delay to allow concurrent requests to use them
        setTimeout(() => {
          this.cachedAccessToken = null;
          this.cachedRefreshToken = null;
        }, 1000);
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
      const refreshResult = await this.refreshTokenIfNeeded();
      if (!refreshResult.success) {
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

      // Handle 401 Unauthorized or 403 Forbidden - attempt token refresh and retry once
      // 403 can occur when API restarts and token validation fails temporarily
      if ((response.status === 401 || response.status === 403) && !endpoint.startsWith('/auth/')) {
        console.log(`[API] Got ${response.status}, attempting token refresh...`);

        // Store the original access token to detect if refresh actually happened
        const originalAccessToken = storage.getAuthToken();

        // If a refresh is already in progress, wait for it instead of starting a new one
        // This prevents multiple concurrent requests from using the same (soon-to-be-revoked) refresh token
        let refreshResult: RefreshResult;

        if (this.isRefreshing && this.refreshPromise) {
          console.log('[API] Waiting for in-flight refresh to complete...');
          refreshResult = await this.refreshPromise;
        } else {
          // No refresh in progress, start one
          // Use cached refresh token if available (from a recent refresh), otherwise get from storage
          const refreshToken = this.cachedRefreshToken || storage.getRefreshToken();
          if (!refreshToken) {
            console.error('[API] No refresh token available, redirecting to login');
            this.redirectToLogin();
            return {
              error: 'Authentication failed',
              status: response.status,
            };
          }

          // Gate concurrent 401/403 handlers with isRefreshing flag
          if (this.isRefreshing && this.refreshPromise) {
            console.log('[API] Refresh started by another request, waiting...');
            refreshResult = await this.refreshPromise;
          } else {
            this.isRefreshing = true;
            this.refreshPromise = (async () => {
              try {
                const refreshResponse = await fetch(`${API_BASE_URL}/auth/refresh`, {
                  method: 'POST',
                  headers: { 'Content-Type': 'application/json' },
                  body: JSON.stringify({ refreshToken }),
                });

                if (!refreshResponse.ok) {
                  console.error('[API] Token refresh failed, redirecting to login');
                  return { success: false };
                }

                const refreshData = await refreshResponse.json();

                // Cache tokens in memory for concurrent requests
                this.cachedAccessToken = refreshData.accessToken;
                this.cachedRefreshToken = refreshData.refreshToken || null;

                // Update stored tokens
                storage.setAuthToken(refreshData.accessToken);
                if (refreshData.refreshToken) {
                  storage.setRefreshToken(refreshData.refreshToken);
                }

                console.log('[API] Token refreshed successfully in 401 handler');
                return {
                  success: true,
                  accessToken: refreshData.accessToken,
                  refreshToken: refreshData.refreshToken
                };
              } catch (error) {
                console.error('[API] Token refresh error:', error);
                return { success: false };
              } finally {
                this.isRefreshing = false;
                this.refreshPromise = null;
                // Clear cached tokens after a short delay
                setTimeout(() => {
                  this.cachedAccessToken = null;
                  this.cachedRefreshToken = null;
                }, 1000);
              }
            })();

            refreshResult = await this.refreshPromise;
          }
        }

        if (!refreshResult.success) {
          console.error('[API] Token refresh failed, redirecting to login');
          this.redirectToLogin();
          return {
            error: 'Session expired',
            status: response.status,
          };
        }

        // Use the fresh access token from the refresh result, or cached token, or storage
        const newAccessToken = refreshResult.accessToken || this.cachedAccessToken || storage.getAuthToken();

        // Safety check: verify the token actually changed to prevent infinite retry loops
        if (newAccessToken === originalAccessToken) {
          console.warn('[API] Token refresh did not produce a new token, not retrying');
          return {
            error: 'Authentication failed',
            status: response.status,
          };
        }

        console.log('[API] Token refreshed, retrying original request');

        // Retry original request with new token
        const newHeaders: HeadersInit = {
          'Content-Type': 'application/json',
          Authorization: `Bearer ${newAccessToken}`,
          ...options.headers,
        };

        const retryResponse = await fetch(`${API_BASE_URL}${endpoint}`, {
          ...options,
          headers: newHeaders,
        });

        const retryData = await retryResponse.json().catch(() => null);

        // Safety check: if retry also fails with 401, don't retry again
        if (retryResponse.status === 401 || retryResponse.status === 403) {
          console.error('[API] Retry request also failed with auth error, not retrying again');
          return {
            error: retryData?.message || 'Authentication failed',
            status: retryResponse.status,
          };
        }

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
  async exchangeToken(checkInToken: string) {
    return this.request('/check-in/teams', {
      method: 'GET',
      headers: {
        Authorization: `Bearer ${checkInToken}`,
      },
    });
  }

  async selectTeam(checkInToken: string, teamId: string) {
    return this.request('/check-in/exchange-token', {
      method: 'POST',
      headers: {
        Authorization: `Bearer ${checkInToken}`,
      },
      body: JSON.stringify({ teamId }),
    });
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

  async updateEmergencyPhones(teamId: string, phoneNumbers: string[]) : Promise<ApiResponse<AdminTeam>> {
    return this.put(`/teams/${teamId}/emergency-phones`, { phoneNumbers });
  }

  async updateParticipants(teamId: string, participants: string[]) : Promise<ApiResponse<AdminTeam>> {
    return this.put(`/teams/${teamId}/participants`, { participants });
  }

  async checkInTeam(teamId: string) : Promise<ApiResponse<AdminTeam>> {
    return this.post(`/teams/${teamId}/check-in`);
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
      participants: data.participants,
      emergencyPhoneNumbers: data.emergencyPhoneNumbers,
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

  async createGame(data: GameRequest) : Promise<ApiResponse<Game>> {
    return this.post('/games', data);
  }

  async generateGameCheckInLink(gameId: string): Promise<ApiResponse<{ url: string; token: string; expiresAt: number; expiryReason: string }>> {
    return this.post(`/games/${gameId}/check-in-link`);
  }

  async updateGame(gameId: string, data: GameRequest) : Promise<ApiResponse<Game>> {
    return this.put(`/games/${gameId}`, data);
  }

  async deleteGame(gameId: string) : Promise<ApiResponse<void>> {
    return this.delete(`/games/${gameId}`);
  }

  async importTeams(gameId: string) : Promise<ApiResponse<TeamImportResult>> {
    return this.post(`/games/${gameId}/import-teams`);
  }

  async testLocationEndpoint(gameId: string) : Promise<ApiResponse<LocationTestResult>> {
    return this.post(`/games/${gameId}/test-location-endpoint`);
  }

  // External API endpoints
  async getExternalEvents() : Promise<ApiResponse<ExternalEvent[]>> {
    return this.get('/external/events');
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

  // Admin User Management endpoints
  async listAdmins(): Promise<ApiResponse<AdminUser[]>> {
    return this.get('/admins');
  }

  async createAdmin(request: CreateAdminRequest): Promise<ApiResponse<AdminUser>> {
    return this.post('/admins', request);
  }

  async updateAdminRoles(adminId: string, request: UpdateAdminRolesRequest): Promise<ApiResponse<AdminUser>> {
    return this.put(`/admins/${adminId}/roles`, request);
  }

  async resetAdminPassword(adminId: string): Promise<ApiResponse<ResetPasswordResponse>> {
    return this.put(`/admins/${adminId}/reset-password`);
  }

  async deactivateAdmin(adminId: string): Promise<ApiResponse<void>> {
    return this.delete(`/admins/${adminId}`);
  }

  // WebSocket Token endpoints
  async getAdminWebSocketToken(): Promise<ApiResponse<{ token: string; expiresInSeconds: number }>> {
    return this.get('/ws/token/admin');
  }

  async getTeamWebSocketToken(gameId: string, teamId: string): Promise<ApiResponse<{ token: string; expiresInSeconds: number }>> {
    return this.get(`/ws/token/team/${gameId}/${teamId}`);
  }
}

// Export either the real API or mock API based on configuration
const api = new ApiService();
export default api;
