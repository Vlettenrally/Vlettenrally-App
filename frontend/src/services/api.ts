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
  TrackerAssignResult,
  Tracker,
  TrackerExportOptions,
  TeamExportOptions,
  AddTrackersResult,
  DeleteTrackersResult,
  LocationTestResult,
  BackfillResult,
  TeamLocation,
  Checkpoint,
  CheckpointRequest,
  CheckpointImportResult,
  AdminTeam,
  PlayerTeam,
  PublicTeam,
  AdminMessage,
  AdminMessageListResponse,
  AdminInboxNotificationListResponse,
  MessageTemplate,
  SendAdminMessageRequest,
  MessageTemplateRequest,
  RetryMessageResponse,
  AdminUser,
  CreateAdminRequest,
  UpdateAdminRolesRequest,
  ResetPasswordResponse,
  AdminReferences,
  Achievement,
  CheckpointTeamStatus,
  PointEntry,
  RankingEntry,
  PointEntryListResponse,
  PointEntryListParams,
  CreatePointAdjustmentRequest,
  EditPointEntryRequest,
  TodayGame,
  TeamOption,
  RegisterDeviceResponse,
  CheckInState,
  CheckInDevice,
  TeamInviteLink,
  TeamInviteResolve,
  FcmTokenRegistrationResponse,
  AutomationRule,
  AutomationRuleRequest,
  AutomationExecutionListResponse,
  CopyAutomationsResult,
  CopyCheckpointsResult,
  Picture,
  PictureCursor,
  PictureDownloadToken,
  PictureListResponse,
  PictureUploadResponse,
  PublicPictureListResponse,
} from '../types/entities';
import storage from './storage';
import { isTokenExpiringSoon } from './auth';
import { suggestDeviceNickname } from '../utils/deviceNickname';

const API_BASE_URL = config.apiBaseUrl;

interface ApiResponse<T> {
  data?: T;
  error?: string;
  status: number;
}

/**
 * How many photos one batch of a picture list holds. Matches the 4-column admin grid, so a
 * batch fills the viewport without making the request pay for presigning more URLs than the
 * user will look at. The photowall omits it and takes the server's configured default.
 */
export const PICTURE_PAGE_SIZE = 24;

/** Build the query string for a cursor-paged picture list, skipping absent parts. */
function pictureQuery(opts: {
  status?: string;
  gameId?: string;
  cursor?: PictureCursor;
  pageSize?: number;
}): string {
  const params = new URLSearchParams();
  if (opts.status) params.set('status', opts.status);
  if (opts.gameId) params.set('gameId', opts.gameId);
  if (opts.cursor) {
    params.set('cursorAt', opts.cursor.at);
    params.set('cursorId', opts.cursor.id);
  }
  if (opts.pageSize) params.set('pageSize', String(opts.pageSize));
  const query = params.toString();
  return query ? `?${query}` : '';
}

/**
 * Outcome of a refresh attempt.
 * - `ok`: refresh succeeded (or another tab already produced a fresh token).
 * - `invalid`: the refresh token was definitively rejected (401) — the session must end.
 * - `network`: a transient failure (network error / 5xx). Tokens are KEPT so a later
 *   request can retry; we must not log the user out over a blip.
 */
type RefreshReason = 'ok' | 'invalid' | 'network';

interface RefreshResult {
  success: boolean;
  reason: RefreshReason;
  accessToken?: string;
  refreshToken?: string;
}

class ApiService {
  private isRefreshing = false;
  private refreshPromise: Promise<RefreshResult> | null = null;
  private cachedAccessToken: string | null = null;

  /**
   * Refresh token if needed (proactive refresh).
   * Returns a refresh result; only refreshes when the access token is expiring soon.
   */
  private async refreshTokenIfNeeded(): Promise<RefreshResult> {
    const token = storage.getAuthToken();
    const refreshToken = storage.getRefreshToken();

    // No tokens, can't refresh
    if (!token || !refreshToken) {
      return { success: false, reason: 'invalid' };
    }

    // Token not expiring soon, no need to refresh
    if (!isTokenExpiringSoon(token)) {
      return { success: true, reason: 'ok' };
    }

    return this.coordinatedRefresh(token);
  }

  /**
   * Coordinate a single refresh across concurrent requests (per-tab) AND across browser
   * tabs (via the Web Locks API). This prevents two callers from presenting the same
   * rotating refresh token at once, which the backend would otherwise treat as reuse.
   */
  private coordinatedRefresh(triggeringAccessToken: string | null): Promise<RefreshResult> {
    // Per-tab single-flight: reuse an in-flight refresh.
    if (this.isRefreshing && this.refreshPromise) {
      return this.refreshPromise;
    }

    this.isRefreshing = true;
    this.refreshPromise = this.runRefreshWithLock(triggeringAccessToken);
    return this.refreshPromise;
  }

  private async runRefreshWithLock(triggeringAccessToken: string | null): Promise<RefreshResult> {
    try {
      const locks =
        typeof navigator !== 'undefined' && navigator.locks && typeof navigator.locks.request === 'function'
          ? navigator.locks
          : null;

      // Cross-tab single-flight: only one tab holds the lock and hits /auth/refresh; the
      // others wait, then re-read the freshly-rotated token from storage inside performRefresh.
      if (locks) {
        return await locks.request('vlettenrally-token-refresh', () =>
          this.performRefresh(triggeringAccessToken)
        );
      }
      return await this.performRefresh(triggeringAccessToken);
    } finally {
      this.isRefreshing = false;
      this.refreshPromise = null;
      // Keep the just-issued token available to concurrent in-flight requests briefly.
      setTimeout(() => {
        this.cachedAccessToken = null;
      }, 1000);
    }
  }

  /**
   * Perform the actual refresh. Always re-reads tokens from storage so that a token another
   * tab rotated while we waited for the lock is picked up instead of a stale captured value.
   */
  private async performRefresh(triggeringAccessToken: string | null): Promise<RefreshResult> {
    // Another tab already rotated while we waited for the lock: use its fresh token, no network.
    const currentAccess = storage.getAuthToken();
    if (currentAccess && currentAccess !== triggeringAccessToken && !isTokenExpiringSoon(currentAccess)) {
      console.log('[API] Token already refreshed by another tab, reusing');
      return { success: true, reason: 'ok', accessToken: currentAccess };
    }

    const refreshToken = storage.getRefreshToken();
    if (!refreshToken) {
      return { success: false, reason: 'invalid' };
    }

    try {
      const response = await fetch(`${API_BASE_URL}/auth/refresh`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ refreshToken }),
      });

      // Definitive rejection: the refresh token is invalid/expired/revoked. End the session.
      if (response.status === 401) {
        console.error('[API] Refresh token rejected (401)');
        return { success: false, reason: 'invalid' };
      }

      // Transient failure (5xx, etc.): keep tokens so a later request can retry.
      if (!response.ok) {
        console.warn('[API] Token refresh transient failure:', response.status);
        return { success: false, reason: 'network' };
      }

      const data = await response.json();

      // Cache the access token in memory for concurrent requests, then persist.
      this.cachedAccessToken = data.accessToken;
      storage.setAuthToken(data.accessToken);
      if (data.refreshToken) {
        storage.setRefreshToken(data.refreshToken);
      }

      console.log('[API] Token refreshed successfully');
      return {
        success: true,
        reason: 'ok',
        accessToken: data.accessToken,
        refreshToken: data.refreshToken,
      };
    } catch (error) {
      // Network error — do NOT discard tokens; this is exactly the flaky-network case.
      console.error('[API] Token refresh network error:', error);
      return { success: false, reason: 'network' };
    }
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

        // Coordinated refresh (single-flight per tab + cross-tab via Web Locks). This is the
        // only place we hit /auth/refresh for a 401/403, so the rotating refresh token is
        // never presented twice concurrently.
        const refreshResult = await this.coordinatedRefresh(originalAccessToken);

        if (!refreshResult.success) {
          if (refreshResult.reason === 'invalid') {
            // The refresh token itself was rejected — the session is genuinely over.
            console.error('[API] Refresh token invalid, redirecting to login');
            this.redirectToLogin();
            return {
              error: 'Session expired',
              status: response.status,
            };
          }
          // Transient/network failure: keep tokens so the next request can retry. Do NOT
          // clear the session — a network blip must never log a team out.
          console.warn('[API] Token refresh failed transiently, keeping session');
          return {
            error: 'Network error',
            status: response.status,
          };
        }

        // Use the fresh access token from the refresh result, or cached token, or storage
        const newAccessToken = refreshResult.accessToken || this.cachedAccessToken || storage.getAuthToken();

        // Safety check: verify the token actually changed to prevent infinite retry loops
        if (!newAccessToken || newAccessToken === originalAccessToken) {
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

  // PATCH request
  async patch<T>(endpoint: string, data?: unknown): Promise<ApiResponse<T>> {
    return this.request<T>(endpoint, {
      method: 'PATCH',
      body: JSON.stringify(data),
    });
  }

  // DELETE request
  async delete<T>(endpoint: string): Promise<ApiResponse<T>> {
    return this.request<T>(endpoint, { method: 'DELETE' });
  }

  // Public self-service check-in flow
  async getCheckInGamesToday(): Promise<ApiResponse<TodayGame[]>> {
    return this.get('/check-in/games/today');
  }

  async getCheckInTeams(gameId: string): Promise<ApiResponse<TeamOption[]>> {
    return this.get(`/check-in/games/${gameId}/teams`);
  }

  async registerCheckInDevice(payload: {
    gameId: string;
    teamId: string;
    deviceId: string | null;
    nickname: string;
    // Present when joining via a scanned team/admin QR — bypasses the time gate.
    inviteToken?: string | null;
  }): Promise<ApiResponse<RegisterDeviceResponse>> {
    return this.post('/check-in/register-device', payload);
  }

  async getCheckInState(): Promise<ApiResponse<CheckInState>> {
    return this.get('/check-in/state');
  }

  // Resolve a scanned invite token to its game/team so the flow can skip the pickers.
  async resolveCheckInInvite(token: string): Promise<ApiResponse<TeamInviteResolve>> {
    return this.get(`/check-in/invite/${encodeURIComponent(token)}`);
  }

  // Team self-service: mint an invite link/QR to share with teammates.
  async getTeamInviteToken(): Promise<ApiResponse<TeamInviteLink>> {
    return this.post('/check-in/invite-token', {});
  }

  // Admin: mint the same kind of invite link/QR for any team.
  async getAdminTeamInviteToken(teamId: string): Promise<ApiResponse<TeamInviteLink>> {
    return this.post(`/teams/${teamId}/invite-token`, {});
  }

  // Admin: per-team device coordination
  async getTeamDevices(teamId: string): Promise<ApiResponse<CheckInDevice[]>> {
    return this.get(`/teams/${teamId}/devices`);
  }

  async setMainTeamDevice(teamId: string, deviceId: string): Promise<ApiResponse<CheckInState>> {
    return this.put(`/teams/${teamId}/devices/main`, { deviceId });
  }

  async resetTeamDevices(teamId: string): Promise<ApiResponse<void>> {
    return this.delete(`/teams/${teamId}/devices`);
  }

  async removeTeamDevice(teamId: string, deviceId: string): Promise<ApiResponse<void>> {
    return this.delete(`/teams/${teamId}/devices/${deviceId}`);
  }

  async resetTeamPointsLedger(teamId: string, gameId: string): Promise<ApiResponse<void>> {
    return this.delete(`/teams/${teamId}/point-entries?gameId=${gameId}`);
  }

  // Admin self-service: their own registered FCM devices
  async getMyAdminDevices(): Promise<ApiResponse<CheckInDevice[]>> {
    return this.get('/admin/me/devices');
  }

  async renameMyAdminDevice(deviceId: string, nickname: string): Promise<ApiResponse<CheckInDevice>> {
    return this.patch(`/admin/me/devices/${deviceId}`, { nickname });
  }

  async removeMyAdminDevice(deviceId: string): Promise<ApiResponse<void>> {
    return this.delete(`/admin/me/devices/${deviceId}`);
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

  async setRivalTeams(teamId: string, rivalTeamIds: string[]) : Promise<ApiResponse<PublicTeam[]>> {
    return this.put(`/teams/${teamId}/rivals`, { rivalTeamIds });
  }

  async updateTeam(teamId: string, data: Partial<AdminTeam>) : Promise<ApiResponse<AdminTeam>> {
    return this.put(`/teams/${teamId}`, data);
  }

  // Assign a tracker from the game's pool to a team, or clear it (trackerId null).
  async assignTeamTracker(teamId: string, trackerId: string | null) : Promise<ApiResponse<AdminTeam>> {
    return this.put(`/teams/${teamId}/tracker`, { trackerId });
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

  async deleteTeam(teamId: string) : Promise<ApiResponse<void>> {
    return this.delete(`/teams/${teamId}`);
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

  async updateGame(gameId: string, data: GameRequest) : Promise<ApiResponse<Game>> {
    return this.put(`/games/${gameId}`, data);
  }

  async deleteGame(gameId: string) : Promise<ApiResponse<void>> {
    return this.delete(`/games/${gameId}`);
  }

  async importTeams(gameId: string) : Promise<ApiResponse<TeamImportResult>> {
    return this.post(`/games/${gameId}/import-teams`);
  }

  // Distribute free trackers from the global pool to teams without one.
  async assignTrackers(gameId: string) : Promise<ApiResponse<TrackerAssignResult>> {
    return this.post(`/games/${gameId}/assign-trackers`);
  }

  // Enabled trackers in the global pool not assigned to any team (picker options).
  async getAvailableTrackers(gameId: string) : Promise<ApiResponse<string[]>> {
    return this.get(`/games/${gameId}/trackers/available`);
  }

  // ==================== Global tracker pool ====================

  async getTrackers() : Promise<ApiResponse<Tracker[]>> {
    return this.get('/trackers');
  }

  async addTrackers(trackerIds: string[]) : Promise<ApiResponse<AddTrackersResult>> {
    return this.post('/trackers', { trackerIds });
  }

  // A POST rather than a DELETE because it carries a body of tracker IDs.
  async deleteTrackers(trackerIds: string[]) : Promise<ApiResponse<DeleteTrackersResult>> {
    return this.post('/trackers/delete', { trackerIds });
  }

  async setTrackerEnabled(trackerId: string, enabled: boolean) : Promise<ApiResponse<Tracker>> {
    return this.put(`/trackers/${encodeURIComponent(trackerId)}/enabled`, { enabled });
  }

  // The CSV exports return a raw file body rather than the JSON envelope, so they bypass
  // the request helpers and carry the bearer token themselves. Kept as a Blob rather than
  // decoded to text: reading the body as text drops any UTF-8 BOM the server wrote, and
  // the ranking export needs that BOM to survive to disk for Excel.
  private async fetchFile(path: string): Promise<{ file?: Blob; error?: string }> {
    const token = storage.getAuthToken();
    try {
      const response = await fetch(`${API_BASE_URL}${path}`, {
        headers: { ...(token && { Authorization: `Bearer ${token}` }) },
      });
      if (!response.ok) {
        return { error: `Export mislukt (${response.status})` };
      }
      return { file: await response.blob() };
    } catch (error) {
      return { error: error instanceof Error ? error.message : 'Network error' };
    }
  }

  // Returns the raw CSV file (not the JSON envelope); the caller triggers the download.
  // Without options the server falls back to its default columns.
  async exportTrackers(
    gameId: string,
    options?: TrackerExportOptions,
  ): Promise<{ file?: Blob; error?: string }> {
    // field/header are parallel lists: the server pairs them by index, so order matters.
    const params = new URLSearchParams();
    options?.columns.forEach(({ field, header }) => {
      params.append('field', field);
      params.append('header', header);
    });
    if (options?.includeUnassigned) {
      params.append('includeUnassigned', 'true');
    }
    const query = params.toString();
    return this.fetchFile(`/games/${gameId}/trackers/export${query ? `?${query}` : ''}`);
  }

  // Same shape as exportTrackers, but team-scoped: not the provider-facing tracker
  // mapping, so it's the one that supports filtering to recently-changed teams.
  async exportTeams(
    gameId: string,
    options: TeamExportOptions,
  ): Promise<{ file?: Blob; error?: string }> {
    const params = new URLSearchParams();
    options.columns.forEach(({ field, header }) => {
      params.append('field', field);
      params.append('header', header);
    });
    if (options.includeUnassigned) {
      params.append('includeUnassigned', 'true');
    }
    if (options.since) {
      params.append('since', options.since);
    }
    const query = params.toString();
    return this.fetchFile(`/games/${gameId}/teams/export${query ? `?${query}` : ''}`);
  }

  // The standings as a CSV file: place, boat number, scouting group, notes and points.
  // Server-generated because it carries the admin-only team notes, which the ranking
  // payload this page renders deliberately does not.
  async exportRankings(gameId: string): Promise<{ file?: Blob; error?: string }> {
    return this.fetchFile(`/games/${gameId}/rankings/export`);
  }

  async testLocationEndpoint(gameId: string) : Promise<ApiResponse<LocationTestResult>> {
    return this.post(`/games/${gameId}/test-location-endpoint`);
  }

  // Backfill a single team's historical track from the external tracker. Throttled server-side;
  // a 429 status means the team is still cooling down.
  async backfillTeamTrack(gameId: string, teamId: string) : Promise<ApiResponse<BackfillResult>> {
    return this.post(`/games/${gameId}/teams/${teamId}/backfill-track`);
  }

  // Location endpoints
  async getLatestTeamLocations(gameId: string): Promise<ApiResponse<TeamLocation[]>> {
    return this.get(`/games/${gameId}/locations/latest`);
  }

  async getMyLatestLocation(gameId: string, teamId: string): Promise<ApiResponse<TeamLocation>> {
    return this.get(`/games/${gameId}/locations/team/${teamId}/latest`);
  }

  async getTeamLocationHistory(
    gameId: string,
    teamId: string,
    windowMinutes: number,
  ): Promise<ApiResponse<TeamLocation[]>> {
    return this.get(`/games/${gameId}/locations/team/${teamId}/history?windowMinutes=${windowMinutes}`);
  }

  async getCheckpoints(gameId: string): Promise<ApiResponse<Checkpoint[]>> {
    return this.get(`/games/${gameId}/checkpoints`);
  }

  async createCheckpoint(gameId: string, data: CheckpointRequest): Promise<ApiResponse<Checkpoint>> {
    return this.post(`/games/${gameId}/checkpoints`, data);
  }

  async updateCheckpoint(gameId: string, checkpointId: string, data: CheckpointRequest): Promise<ApiResponse<Checkpoint>> {
    return this.put(`/games/${gameId}/checkpoints/${checkpointId}`, data);
  }

  async deleteCheckpoint(gameId: string, checkpointId: string): Promise<ApiResponse<void>> {
    return this.delete(`/games/${gameId}/checkpoints/${checkpointId}`);
  }

  async importCheckpoints(gameId: string): Promise<ApiResponse<CheckpointImportResult>> {
    return this.post(`/games/${gameId}/import-checkpoints`);
  }

  async copyCheckpoints(gameId: string, sourceGameId: string): Promise<ApiResponse<CopyCheckpointsResult>> {
    return this.post(`/games/${gameId}/copy-checkpoints`, { sourceGameId });
  }

  // Automation endpoints (admin)

  async getAutomations(gameId: string): Promise<ApiResponse<AutomationRule[]>> {
    return this.get(`/games/${gameId}/automations`);
  }

  async createAutomation(gameId: string, data: AutomationRuleRequest): Promise<ApiResponse<AutomationRule>> {
    return this.post(`/games/${gameId}/automations`, data);
  }

  async updateAutomation(gameId: string, ruleId: string, data: AutomationRuleRequest): Promise<ApiResponse<AutomationRule>> {
    return this.put(`/games/${gameId}/automations/${ruleId}`, data);
  }

  async deleteAutomation(gameId: string, ruleId: string): Promise<ApiResponse<void>> {
    return this.delete(`/games/${gameId}/automations/${ruleId}`);
  }

  async setAutomationEnabled(gameId: string, ruleId: string, enabled: boolean): Promise<ApiResponse<AutomationRule>> {
    return this.patch(`/games/${gameId}/automations/${ruleId}/enabled`, { enabled });
  }

  async getAutomationExecutions(
    gameId: string,
    ruleId: string,
    page = 0,
    pageSize = 25,
  ): Promise<ApiResponse<AutomationExecutionListResponse>> {
    return this.get(`/games/${gameId}/automations/${ruleId}/executions?page=${page}&pageSize=${pageSize}`);
  }

  async copyAutomations(gameId: string, sourceGameId: string): Promise<ApiResponse<CopyAutomationsResult>> {
    return this.post(`/games/${gameId}/automations/copy`, { sourceGameId });
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

  async getAdminInboxByGame(
    gameId: string,
    page: number = 0,
    pageSize: number = 20
  ): Promise<ApiResponse<AdminInboxNotificationListResponse>> {
    return this.get(`/messages/admin/inbox/games/${gameId}?page=${page}&pageSize=${pageSize}`);
  }

  async markAdminInboxNotificationAsRead(notificationId: string): Promise<ApiResponse<{ success: boolean }>> {
    return this.put(`/messages/admin/inbox/${notificationId}/read`, {});
  }

  async markAllAdminInboxNotificationsAsRead(gameId: string): Promise<ApiResponse<{ success: boolean }>> {
    return this.put(`/messages/admin/inbox/games/${gameId}/read-all`, {});
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
  async getRankings(gameId: string): Promise<ApiResponse<RankingEntry[]>> {
    return this.get(`/games/${gameId}/rankings`);
  }

  // Reveal the final ranking to players (admin only). Persists the reveal and
  // pushes the standings to connected teams.
  async publishRankings(gameId: string): Promise<ApiResponse<Game>> {
    return this.post(`/games/${gameId}/rankings/publish`);
  }

  /**
   * Emergency stop: freeze the competition without stopping tracking. Answers 409 when the
   * game is not running. Separate from updateGame because pausing also opens the pause period
   * that keeps the frozen stretch out of the distance totals.
   */
  async pauseGame(gameId: string, reason?: string): Promise<ApiResponse<Game>> {
    return this.post(`/games/${gameId}/pause`, { reason });
  }

  /** Resume a paused game. Answers 409 when the game is not paused. */
  async resumeGame(gameId: string): Promise<ApiResponse<Game>> {
    return this.post(`/games/${gameId}/resume`);
  }

  /**
   * Mint the token that unlocks this game's public narrowcasting map. The token is derived, not
   * stored, so this is safe to call repeatedly — it always returns the same value. Answers 503 when
   * the public API has no secret configured, i.e. a link would be dead on arrival.
   */
  async getPublicMapToken(gameId: string): Promise<ApiResponse<{ token: string }>> {
    return this.get(`/games/${gameId}/public-map-token`);
  }

  // Achievements endpoints
  async getTeamAchievements(teamId: string) {
    return this.get(`/teams/${teamId}/achievements`);
  }

  async getTeamVerifiedAchievements(teamId: string) {
    return this.get(`/teams/${teamId}/achievements/verified`);
  }

  async getTeamsNearCheckpoint(gameId: string, checkpointId: string): Promise<ApiResponse<CheckpointTeamStatus[]>> {
    return this.get(`/games/${gameId}/checkpoints/${checkpointId}/teams/nearby`);
  }

  async verifyAchievement(achievementId: string, notes?: string, bonusPoints?: number): Promise<ApiResponse<Achievement>> {
    return this.post(`/achievements/${achievementId}/verify`, { notes, bonusPoints });
  }

  async rejectAchievement(achievementId: string, notes: string): Promise<ApiResponse<Achievement>> {
    return this.post(`/achievements/${achievementId}/reject`, { notes });
  }

  async adjustBonusPoints(achievementId: string, bonusDelta: number): Promise<ApiResponse<Achievement>> {
    return this.post(`/achievements/${achievementId}/bonus-points`, { bonusDelta });
  }

  async detectProximity(teamId: string, checkpointId: string): Promise<ApiResponse<Achievement>> {
    return this.post('/achievements/detect', { teamId, checkpointId });
  }

  /**
   * Add a team to a checkpoint as verified in one step. Unlike detectProximity this
   * never conflicts: it overrides an existing pending/rejected entry, and adding an
   * already-verified team is a no-op.
   */
  async recordManualPass(teamId: string, checkpointId: string): Promise<ApiResponse<Achievement>> {
    return this.post('/achievements/manual', { teamId, checkpointId });
  }

  // FCM Token registration
  async registerFcmToken(token: string): Promise<ApiResponse<FcmTokenRegistrationResponse>> {
    // Admins and teams have separate device-row keys (one device_registrations row
    // cannot belong to both at once); pick the right one based on the active role.
    // The id we send is a HINT for resuming an existing row — never a directive.
    const isAdmin = storage.isAdmin();
    const deviceIdHint = isAdmin
      ? storage.getAdminDeviceId()
      : storage.getDeviceId();
    const payload: Record<string, unknown> = {
      token,
      nickname: suggestDeviceNickname(),
    };
    if (deviceIdHint) {
      payload.deviceId = deviceIdHint;
    }
    const response = await this.post<FcmTokenRegistrationResponse>('/fcm-tokens', payload);
    // Persist the server-assigned deviceId so subsequent calls resume the same row.
    if (response.data?.deviceId) {
      if (isAdmin) {
        storage.setAdminDeviceId(response.data.deviceId);
      } else {
        storage.setDeviceId(response.data.deviceId);
      }
    }
    return response;
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

  async deactivateAdmin(adminId: string): Promise<ApiResponse<AdminUser>> {
    return this.put(`/admins/${adminId}/deactivate`);
  }

  async activateAdmin(adminId: string): Promise<ApiResponse<AdminUser>> {
    return this.put(`/admins/${adminId}/activate`);
  }

  /** Counts the content attributed to an admin; all zeroes means it can be deleted. */
  async getAdminReferences(adminId: string): Promise<ApiResponse<AdminReferences>> {
    return this.get(`/admins/${adminId}/references`);
  }

  /** Permanent delete. Only succeeds for a deactivated admin with no attributed content. */
  async deleteAdmin(adminId: string): Promise<ApiResponse<void>> {
    return this.delete(`/admins/${adminId}`);
  }

  // WebSocket Token endpoints
  async getAdminWebSocketToken(): Promise<ApiResponse<{ token: string; expiresInSeconds: number }>> {
    return this.get('/ws/token/admin');
  }

  async getTeamWebSocketToken(gameId: string, teamId: string): Promise<ApiResponse<{ token: string; expiresInSeconds: number }>> {
    return this.get(`/ws/token/team/${gameId}/${teamId}`);
  }

  // Points ledger endpoints
  async getPointEntries(gameId: string, params: PointEntryListParams = {}): Promise<ApiResponse<PointEntryListResponse>> {
    const search = new URLSearchParams({ gameId });
    if (params.teamId) search.set('teamId', params.teamId);
    if (params.entryType) search.set('entryType', params.entryType);
    if (params.page !== undefined) search.set('page', String(params.page));
    if (params.pageSize !== undefined) search.set('pageSize', String(params.pageSize));
    return this.get(`/admin/point-entries?${search.toString()}`);
  }

  async createPointAdjustment(request: CreatePointAdjustmentRequest): Promise<ApiResponse<PointEntry>> {
    return this.post('/admin/point-entries', request);
  }

  async editPointEntry(entryId: string, request: EditPointEntryRequest): Promise<ApiResponse<PointEntry>> {
    return this.patch(`/admin/point-entries/${entryId}`, request);
  }

  // ==================== Picture endpoints ====================
  // The list endpoints are cursor-paged; see pictureQuery below.

  // Team: reserve an upload slot and get a pre-signed PUT URL.
  async requestPictureUpload(
    teamId: string,
    body: { contentType: string; fileName?: string; sizeBytes?: number },
  ): Promise<ApiResponse<PictureUploadResponse>> {
    return this.post(`/teams/${teamId}/pictures`, body);
  }

  /**
   * Upload a file directly to S3 using a pre-signed PUT URL.
   *
   * Deliberately bypasses the request() wrapper: this goes straight to the storage
   * provider, so it must NOT carry our Authorization header, and it needs upload
   * progress (which fetch cannot report). Uses a raw XMLHttpRequest. The Content-Type
   * must match the type the URL was signed with, or S3 rejects the signature.
   */
  uploadFileToS3(
    uploadUrl: string,
    file: File,
    contentType: string,
    onProgress?: (percent: number) => void,
  ): Promise<{ ok: boolean; status: number }> {
    return new Promise((resolve) => {
      const xhr = new XMLHttpRequest();
      xhr.open('PUT', uploadUrl, true);
      xhr.setRequestHeader('Content-Type', contentType);
      if (onProgress) {
        xhr.upload.onprogress = (event) => {
          if (event.lengthComputable) {
            onProgress(Math.round((event.loaded / event.total) * 100));
          }
        };
      }
      xhr.onload = () => resolve({ ok: xhr.status >= 200 && xhr.status < 300, status: xhr.status });
      xhr.onerror = () => resolve({ ok: false, status: xhr.status || 0 });
      xhr.send(file);
    });
  }

  async confirmPictureUpload(teamId: string, pictureId: string): Promise<ApiResponse<Picture>> {
    return this.post(`/teams/${teamId}/pictures/${pictureId}/confirm`);
  }

  async getMyPictures(teamId: string): Promise<ApiResponse<Picture[]>> {
    return this.get(`/teams/${teamId}/pictures`);
  }

  // Players' photowall: all approved pictures for the team's game, newest first.
  // Returns the public-safe projection (no moderation metadata) — it's a shared wall.
  // Paged: pass the cursor from the previous page to load older photos.
  async getPhotowall(
    teamId: string,
    cursor?: PictureCursor,
  ): Promise<ApiResponse<PublicPictureListResponse>> {
    return this.get(`/teams/${teamId}/pictures/approved${pictureQuery({ cursor })}`);
  }

  // Admin moderation. All three are cursor-paged; omit the cursor for the first page.
  async getPendingPictures(
    gameId?: string,
    cursor?: PictureCursor,
  ): Promise<ApiResponse<PictureListResponse>> {
    return this.get(`/pictures/pending${pictureQuery({ gameId, cursor, pageSize: PICTURE_PAGE_SIZE })}`);
  }

  async getAllPictures(
    status?: string,
    gameId?: string,
    cursor?: PictureCursor,
  ): Promise<ApiResponse<PictureListResponse>> {
    return this.get(`/pictures${pictureQuery({ status, gameId, cursor, pageSize: PICTURE_PAGE_SIZE })}`);
  }

  async getApprovedPictures(
    gameId?: string,
    cursor?: PictureCursor,
  ): Promise<ApiResponse<PictureListResponse>> {
    return this.get(`/pictures/approved${pictureQuery({ gameId, cursor, pageSize: PICTURE_PAGE_SIZE })}`);
  }

  async approvePicture(pictureId: string): Promise<ApiResponse<Picture>> {
    return this.post(`/pictures/${pictureId}/approve`);
  }

  async rejectPicture(pictureId: string, notes?: string): Promise<ApiResponse<Picture>> {
    return this.post(`/pictures/${pictureId}/reject`, { notes });
  }

  // Admin uploads (auto-approved)
  async requestAdminPictureUpload(
    body: { contentType: string; fileName?: string; sizeBytes?: number },
  ): Promise<ApiResponse<PictureUploadResponse>> {
    return this.post('/admin/pictures', body);
  }

  async confirmAdminPictureUpload(pictureId: string): Promise<ApiResponse<Picture>> {
    return this.post(`/admin/pictures/${pictureId}/confirm`);
  }

  /**
   * Mint a one-time token for the approved-picture archive of a game. Goes through the
   * normal wrapper, so the JWT is refreshed if needed — this is where authorisation happens,
   * because the download itself carries no Authorization header.
   */
  async requestPictureDownloadToken(gameId: string): Promise<ApiResponse<PictureDownloadToken>> {
    return this.post(`/pictures/download-token?gameId=${encodeURIComponent(gameId)}`);
  }

  /**
   * URL to navigate the browser to for the zip. Navigation rather than fetch+blob on purpose:
   * the browser streams the archive straight to disk with a native progress indicator, so a
   * multi-hundred-megabyte download never sits in page memory.
   */
  pictureArchiveUrl(token: string): string {
    return `${API_BASE_URL}/pictures/approved/download?token=${encodeURIComponent(token)}`;
  }
}

// Export either the real API or mock API based on configuration
const api = new ApiService();
export default api;
