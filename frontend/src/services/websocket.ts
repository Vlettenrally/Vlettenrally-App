/**
 * WebSocket Service - Handles real-time communication with the backend
 * 
 * Supports both team and admin connections with automatic reconnection.
 * Frontend sends PING every 10s, backend responds with PONG.
 * Admin connections are game-agnostic and use SWITCH_GAME to select a game.
 */

import api from './api';

const WS_BASE_URL = import.meta.env.VITE_WS_URL || 'ws://localhost:8080/ws';

export const WebSocketEventType = {
  MESSAGE_NEW: 'MESSAGE_NEW',
  MESSAGE_DELIVERY_UPDATE: 'MESSAGE_DELIVERY_UPDATE',
  COUNTDOWN_UPDATE: 'COUNTDOWN_UPDATE',
  TEAM_ACHIEVEMENT: 'TEAM_ACHIEVEMENT',
  RIVAL_UPDATE: 'RIVAL_UPDATE',
  RANKING_UPDATE: 'RANKING_UPDATE',
  GAME_STATUS: 'GAME_STATUS',
  ADMIN_ALERT: 'ADMIN_ALERT',
  TEAM_CHECKIN: 'TEAM_CHECKIN',
  TEAM_UPDATE: 'TEAM_UPDATE',
  PONG: 'PONG',
  GAME_SWITCHED: 'GAME_SWITCHED',
  LOCATION_UPDATE: 'LOCATION_UPDATE',
  POINT_ENTRY_CREATED: 'POINT_ENTRY_CREATED',
  POINT_ENTRY_UPDATED: 'POINT_ENTRY_UPDATED',
  TEAM_LEDGER_RESET: 'TEAM_LEDGER_RESET',
  PICTURE_PENDING: 'PICTURE_PENDING',
  PICTURE_MODERATED: 'PICTURE_MODERATED',
  PICTURE_APPROVED: 'PICTURE_APPROVED',
  CHECKIN_DETAILS_SUBMITTED: 'CHECKIN_DETAILS_SUBMITTED',
  CHECKIN_RESET: 'CHECKIN_RESET',
  CHECKIN_MAIN_CHANGED: 'CHECKIN_MAIN_CHANGED',
  CHECKIN_DEVICE_REMOVED: 'CHECKIN_DEVICE_REMOVED',
  AUTOMATION_TRIGGERED: 'AUTOMATION_TRIGGERED',
  AUTOMATION_NOTIFICATION: 'AUTOMATION_NOTIFICATION',
  ERROR: 'ERROR',
} as const;

export type WebSocketEventTypeValue = typeof WebSocketEventType[keyof typeof WebSocketEventType];

export interface WebSocketMessage {
  type: WebSocketEventTypeValue | string;
  payload: unknown;
  timestamp: string;
  targetGameId?: string;
  targetTeamId?: string;
  adminOnly?: boolean;
}

export type ConnectionType = 'team' | 'admin';

export interface TeamConnectionConfig {
  type: 'team';
  gameId: string;
  teamId: string;
}

export interface AdminConnectionConfig {
  type: 'admin';
  gameId?: string; // Optional - admin connections are game-agnostic
}

export type ConnectionConfig = TeamConnectionConfig | AdminConnectionConfig;

type MessageHandler = (message: WebSocketMessage) => void;
type ConnectionStateHandler = (connected: boolean) => void;

class WebSocketService {
  private socket: WebSocket | null = null;
  private reconnectTimeout: number | null = null;
  private reconnectAttempts = 0;
  private maxReconnectAttempts = 10;
  private reconnectDelay = 1000; // Start with 1 second
  private messageHandlers = new Map<WebSocketEventTypeValue | string | 'all', Set<MessageHandler>>();
  private connectionStateHandlers = new Set<ConnectionStateHandler>();
  private isIntentionallyClosed = false;
  private config: ConnectionConfig | null = null;
  private pingInterval: number | null = null;
  private readonly PING_INTERVAL_MS = 10000; // 10 seconds
  private readonly PONG_TIMEOUT_MS = 15000; // 15 seconds - if no pong within this time, reconnect
  private lastPongReceived: number | null = null;
  private pongTimeoutCheck: number | null = null;
  private isOnline = true;
  private isConnecting = false;
  private connectionGeneration = 0;

  constructor() {
    // Monitor online/offline status
    this.isOnline = navigator.onLine;

    window.addEventListener('online', this.handleOnline);
    window.addEventListener('offline', this.handleOffline);

    // Recover the socket when the app is foregrounded. Backgrounding a mobile tab
    // throttles timers and usually drops the socket, but it does NOT fire 'online'
    // (the network never went down), so the backoff never gets reset on resume.
    document.addEventListener('visibilitychange', this.handleVisibilityChange);
    // pageshow also covers bfcache restore on mobile Safari, where visibilitychange
    // may not fire.
    window.addEventListener('pageshow', this.handleVisibilityChange);
  }

  private handleVisibilityChange = (): void => {
    if (document.visibilityState !== 'visible') return;

    // Resync from the live value rather than trusting the cached flag: mobile
    // browsers can fail to fire a matching 'online' event after a network
    // switch, which would otherwise latch isOnline false forever and disable
    // every recovery path (this is the one that restores the app on
    // foreground, so it must not depend on a possibly-stale reading).
    this.isOnline = navigator.onLine;

    if (!this.config || this.isIntentionallyClosed || !this.isOnline) return;

    // Force a fresh connection whether the socket is dead or a zombie (OPEN but stale
    // pong). forceReconnect() clears isConnecting and increments the generation so any
    // in-flight buildUrl() from a prior attempt is discarded — this is what prevents
    // the deadlock when isConnecting is stuck true during a slow token fetch.
    if (!this.isConnected() ||
        (this.lastPongReceived && Date.now() - this.lastPongReceived > this.PONG_TIMEOUT_MS)) {
      this.forceReconnect();
    }
  };

  private handleOnline = (): void => {
    console.log('Network connection restored');
    this.isOnline = true;
    
    // Immediately attempt reconnection if we were disconnected
    if (this.config && !this.isConnected() && !this.isIntentionallyClosed) {
      this.reconnectAttempts = 0;
      // Discard any in-flight buildUrl() from a prior attempt so it cannot create
      // an orphaned socket once it eventually resolves, and unblock the isConnecting guard.
      this.connectionGeneration++;
      this.isConnecting = false;
      this.createConnection().catch(err => console.error('Reconnection failed:', err));
    }
  };

  private handleOffline = (): void => {
    console.log('Network connection lost');
    this.isOnline = false;
    
    // Stop ping when offline
    this.stopPing();
    
    // Clear any pending reconnection attempts
    if (this.reconnectTimeout) {
      clearTimeout(this.reconnectTimeout);
      this.reconnectTimeout = null;
    }
  };

  /**
   * Connect as a team member
   */
  connectAsTeam(gameId: string, teamId: string): void {
    this.connect({ type: 'team', gameId, teamId });
  }

  /**
   * Connect as an admin (game-agnostic connection)
   * Optionally specify a gameId to automatically switch to that game after connecting
   */
  connectAsAdmin(gameId?: string): void {
    this.connect({ type: 'admin', gameId });
  }

  /**
   * Switch to a different game (admin connections only)
   * Sends SWITCH_GAME message to backend and updates local config
   */
  switchGame(gameId: string): void {
    if (this.config?.type !== 'admin') {
      console.warn('switchGame can only be used with admin connections');
      return;
    }
    
    if (!this.isConnected()) {
      console.warn('Cannot switch game: WebSocket is not connected');
      // Store the gameId so it will be sent on reconnect
      this.config.gameId = gameId;
      return;
    }
    
    this.send({ type: 'SWITCH_GAME', gameId });
    this.config.gameId = gameId;
  }

  /**
   * Generic connect method
   */
  connect(config: ConnectionConfig): void {
    if (this.socket?.readyState === WebSocket.OPEN) {
      // Check if it's the same connection
      if (this.isSameConfig(config)) {
        console.log('WebSocket already connected with same config');
        return;
      }
      // Different config, disconnect first (but don't clear handlers for admin game switches)
      if (config.type === 'admin' && this.config?.type === 'admin') {
        // Admin switching games - just send SWITCH_GAME, don't reconnect
        if (config.gameId) {
          this.switchGame(config.gameId);
        }
        return;
      }
      this.disconnect();
    }

    this.config = config;
    this.isIntentionallyClosed = false;
    this.createConnection().catch(err => console.error('Connection failed:', err));
  }

  private isSameConfig(config: ConnectionConfig): boolean {
    if (!this.config) return false;
    if (this.config.type !== config.type) return false;
    if (config.type === 'team' && this.config.type === 'team') {
      return this.config.gameId === config.gameId && this.config.teamId === config.teamId;
    }
    // For admin connections, we don't compare gameId since it's game-agnostic
    return config.type === 'admin' && this.config.type === 'admin';
  }

  private async buildUrl(): Promise<string> {
    if (!this.config) {
      throw new Error('Connection config not set');
    }

    if (this.config.type === 'team') {
      // Get a short-lived WebSocket token from the API
      const response = await api.getTeamWebSocketToken(this.config.gameId, this.config.teamId);
      if (response.error || !response.data) {
        throw new Error(`Failed to get WebSocket token: ${response.error || 'Unknown error'}`);
      }
      return `${WS_BASE_URL}/team/${response.data.token}`;
    } else {
      // Admin connection - get token from API
      const response = await api.getAdminWebSocketToken();
      if (response.error || !response.data) {
        throw new Error(`Failed to get WebSocket token: ${response.error || 'Unknown error'}`);
      }
      return `${WS_BASE_URL}/admin/${response.data.token}`;
    }
  }

  private async createConnection(): Promise<void> {
    if (!this.config) {
      console.error('Cannot connect: config not set');
      return;
    }

    // Guard against concurrent connection attempts. buildUrl() awaits a token fetch
    // before this.socket is assigned, so without this flag a second trigger (online
    // event, visibility change, or a fired backoff timer) could open a duplicate socket.
    if (this.isConnecting) {
      return;
    }
    this.isConnecting = true;
    // Snapshot the generation so we can detect if disconnect() or forceReconnect()
    // superseded this attempt while buildUrl() was awaiting the token fetch.
    const gen = ++this.connectionGeneration;

    try {
      const url = await this.buildUrl();

      // A disconnect() or forceReconnect() increments connectionGeneration to signal
      // that this attempt is stale. Discard the result rather than creating an orphaned
      // socket that would race with the fresh connection that superseded us.
      if (gen !== this.connectionGeneration) return;

      // Mask token in logs for security
      const maskedUrl = url.replace(/\/[^/]+$/, '/[token]');
      console.log('Connecting to WebSocket:', maskedUrl);

      this.socket = new WebSocket(url);

      this.socket.onopen = () => {
        console.log('WebSocket connected as', this.config?.type);
        // A successful connection is definitive proof of connectivity — correct
        // the cached flag in case it was wrongly latched false by a missed
        // 'online' event.
        this.isOnline = true;
        this.isConnecting = false;
        this.reconnectAttempts = 0;
        this.reconnectDelay = 1000;
        this.lastPongReceived = Date.now();
        this.notifyConnectionState(true);
        this.startPing();
        
        // For admin connections, send SWITCH_GAME if a gameId was specified
        if (this.config?.type === 'admin' && this.config.gameId) {
          this.send({ type: 'SWITCH_GAME', gameId: this.config.gameId });
        }
      };

      this.socket.onmessage = (event) => {
        try {
          const message: WebSocketMessage = JSON.parse(event.data);
          this.handleMessage(message);
        } catch (error) {
          console.error('Failed to parse WebSocket message:', error);
        }
      };

      this.socket.onerror = (error) => {
        console.error('WebSocket error:', error);
      };

      this.socket.onclose = () => {
        console.log('WebSocket disconnected');
        this.isConnecting = false;
        this.socket = null;
        this.stopPing();

        // Only notify disconnection if it wasn't intentional
        // (avoids spurious "disconnected" notifications during route transitions)
        if (!this.isIntentionallyClosed) {
          this.notifyConnectionState(false);
          this.scheduleReconnect();
        }
      };
    } catch (error) {
      if (gen !== this.connectionGeneration) return; // Stale — a newer attempt took over.
      console.error('Failed to create WebSocket connection:', error);
      this.isConnecting = false;
      this.scheduleReconnect();
    }
  }

  private scheduleReconnect(): void {
    if (this.reconnectTimeout) {
      clearTimeout(this.reconnectTimeout);
    }

    // Resync from the live value rather than trusting the cached flag, which
    // can get latched false forever if the browser never fires a matching
    // 'online' event. Even if still offline, fall through to arm the timer
    // below rather than returning — the check inside the timeout re-reads
    // isOnline when it fires, turning this into a polling loop instead of a
    // dead end that depends entirely on an 'online' event that may never come.
    this.isOnline = navigator.onLine;
    if (!this.isOnline) {
      console.log('Skipping reconnection attempt - device is offline');
    }

    // Keep counting attempts only up to the cap; past it we keep retrying at the
    // capped interval rather than giving up forever. A permanent stop here would
    // leave the client showing "server unreachable" even once the server is back,
    // since only the 'online' event or foregrounding would ever reset it.
    if (this.reconnectAttempts < this.maxReconnectAttempts) {
      this.reconnectAttempts++;
    }
    const delay = Math.min(this.reconnectDelay * this.reconnectAttempts, 30000);

    console.log(`Reconnecting in ${delay}ms (attempt ${this.reconnectAttempts})`);

    this.reconnectTimeout = window.setTimeout(() => {
      // Resync again at fire time — if we're still offline, re-arm rather than
      // stopping, so a missed 'online' event degrades to polling navigator.onLine
      // every tick instead of leaving the client permanently unable to recover.
      this.isOnline = navigator.onLine;
      if (!this.isOnline) {
        this.scheduleReconnect();
        return;
      }
      if (!this.isConnecting) {
        this.createConnection().catch(err => console.error('Reconnection failed:', err));
      }
    }, delay);
  }

  /**
   * Reset reconnection state and force a new connection attempt.
   * Call this when you want to force a reconnection after failures.
   */
  resetAndReconnect(): void {
    this.reconnectAttempts = 0;
    this.reconnectDelay = 1000;
    this.isIntentionallyClosed = false;

    if (this.reconnectTimeout) {
      clearTimeout(this.reconnectTimeout);
      this.reconnectTimeout = null;
    }

    if (this.config && this.isOnline && !this.isConnected() && !this.isConnecting) {
      this.createConnection().catch(err => console.error('Manual reconnection failed:', err));
    }
  }

  /**
   * Drop a socket that still reports OPEN but is stale (no recent pong) and
   * reconnect immediately. resetAndReconnect() deliberately no-ops while
   * isConnected() is true, so this path is needed for zombie sockets after resume.
   */
  private forceReconnect(): void {
    // Invalidate any in-flight createConnection() so its buildUrl() result is discarded
    // rather than creating an orphaned socket that races the fresh connection below.
    this.connectionGeneration++;
    this.stopPing();
    if (this.socket) {
      // Clear handlers so the imminent close() doesn't run scheduleReconnect and
      // race with the fresh connection we start below.
      this.socket.onclose = null;
      this.socket.close();
      this.socket = null;
    }
    this.isConnecting = false;
    this.notifyConnectionState(false);
    this.resetAndReconnect();
  }

  private startPing(): void {
    // Clear any existing ping interval
    this.stopPing();
    
    // Only start ping if online
    if (!this.isOnline) {
      return;
    }
    
    // Send initial ping
    this.sendPing();
    
    // Set up interval for periodic pings
    this.pingInterval = window.setInterval(() => {
      if (this.isOnline && this.isConnected()) {
        this.sendPing();
      }
    }, this.PING_INTERVAL_MS);
    
    // Set up pong timeout check
    this.startPongTimeoutCheck();
  }

  private stopPing(): void {
    if (this.pingInterval) {
      clearInterval(this.pingInterval);
      this.pingInterval = null;
    }
    if (this.pongTimeoutCheck) {
      clearInterval(this.pongTimeoutCheck);
      this.pongTimeoutCheck = null;
    }
  }

  private sendPing(): void {
    if (this.socket?.readyState === WebSocket.OPEN) {
      try {
        this.socket.send(JSON.stringify({ type: 'PING' }));
      } catch (error) {
        console.error('Failed to send ping:', error);
      }
    }
  }
  
  private startPongTimeoutCheck(): void {
    // Check every 5 seconds if we've received a pong within the timeout window
    this.pongTimeoutCheck = window.setInterval(() => {
      if (!this.isConnected() || !this.isOnline) {
        return;
      }
      
      const now = Date.now();
      if (this.lastPongReceived && now - this.lastPongReceived > this.PONG_TIMEOUT_MS) {
        console.warn('No PONG received within timeout, reconnecting...');
        this.lastPongReceived = null;
        
        // Force close and reconnect
        if (this.socket) {
          this.socket.close();
        }
      }
    }, 5000);
  }
  
  private handlePong(): void {
    this.lastPongReceived = Date.now();
  }

  private handleMessage(message: WebSocketMessage): void {
    // Handle PONG messages internally
    if (message.type === 'PONG') {
      this.handlePong();
      return;
    }
    
    // Call handlers for specific message type
    const typeHandlers = this.messageHandlers.get(message.type);
    if (typeHandlers) {
      typeHandlers.forEach((handler) => handler(message));
    }

    // Call handlers for all messages
    const allHandlers = this.messageHandlers.get('all');
    if (allHandlers) {
      allHandlers.forEach((handler) => handler(message));
    }
  }

  private notifyConnectionState(connected: boolean): void {
    this.connectionStateHandlers.forEach((handler) => handler(connected));
  }

  /**
   * Subscribe to messages of a specific type
   */
  on(type: WebSocketEventTypeValue | string | 'all', handler: MessageHandler): () => void {
    if (!this.messageHandlers.has(type)) {
      this.messageHandlers.set(type, new Set());
    }
    this.messageHandlers.get(type)!.add(handler);

    // Return unsubscribe function
    return () => {
      this.messageHandlers.get(type)?.delete(handler);
    };
  }

  /**
   * Unsubscribe from messages of a specific type
   */
  off(type: WebSocketEventTypeValue | string | 'all', handler: MessageHandler): void {
    this.messageHandlers.get(type)?.delete(handler);
  }

  /**
   * Subscribe to connection state changes
   */
  onConnectionChange(handler: ConnectionStateHandler): () => void {
    this.connectionStateHandlers.add(handler);
    return () => {
      this.connectionStateHandlers.delete(handler);
    };
  }

  /**
   * Send a message through the WebSocket
   */
  send(message: unknown): void {
    if (this.socket?.readyState === WebSocket.OPEN) {
      this.socket.send(JSON.stringify(message));
    } else {
      console.warn('WebSocket is not connected. Message not sent:', message);
    }
  }

  /**
   * Disconnect from the WebSocket
   * @param clearHandlers - Whether to clear all message/connection handlers (default: true)
   *                        Set to false when reconnecting to preserve subscriptions
   */
  disconnect(clearHandlers = true): void {
    this.isIntentionallyClosed = true;
    this.isConnecting = false;
    this.connectionGeneration++; // Discard any in-flight buildUrl() result.

    if (this.reconnectTimeout) {
      clearTimeout(this.reconnectTimeout);
      this.reconnectTimeout = null;
    }

    this.stopPing();

    if (this.socket) {
      this.socket.close();
      this.socket = null;
    }

    if (clearHandlers) {
      this.messageHandlers.clear();
      this.connectionStateHandlers.clear();
    }
    this.config = null;
    this.lastPongReceived = null;
  }

  /**
   * Cleanup method to remove event listeners (call when service is destroyed)
   */
  destroy(): void {
    this.disconnect();
    window.removeEventListener('online', this.handleOnline);
    window.removeEventListener('offline', this.handleOffline);
    document.removeEventListener('visibilitychange', this.handleVisibilityChange);
    window.removeEventListener('pageshow', this.handleVisibilityChange);
  }

  /**
   * Check if currently connected and healthy (receiving pongs)
   */
  isConnected(): boolean {
    return this.socket?.readyState === WebSocket.OPEN;
  }
  
  /**
   * Check if connection is healthy (has received pong recently)
   */
  isHealthy(): boolean {
    if (!this.isConnected()) {
      return false;
    }
    if (!this.lastPongReceived) {
      return true; // Just connected, give it time
    }
    return Date.now() - this.lastPongReceived < this.PONG_TIMEOUT_MS;
  }

  /**
   * Get the current connection type
   */
  getConnectionType(): ConnectionType | null {
    return this.config?.type ?? null;
  }

  /**
   * Get the current game ID
   */
  getGameId(): string | null {
    return this.config?.gameId ?? null;
  }

  /**
   * Get the current team ID (only for team connections)
   */
  getTeamId(): string | null {
    if (this.config?.type === 'team') {
      return this.config.teamId;
    }
    return null;
  }
}

export const websocket = new WebSocketService();
export default websocket;
