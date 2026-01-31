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
