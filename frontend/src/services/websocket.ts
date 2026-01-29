/**
 * WebSocket Service - Handles real-time communication with the backend
 * 
 * Supports both team and admin connections with automatic reconnection
 */

import { storage } from './storage';

const WS_BASE_URL = import.meta.env.VITE_WS_URL || 'ws://localhost:8080/ws';

export const WebSocketEventType = {
  MESSAGE_NEW: 'MESSAGE_NEW',
  COUNTDOWN_UPDATE: 'COUNTDOWN_UPDATE',
  TEAM_ACHIEVEMENT: 'TEAM_ACHIEVEMENT',
  RIVAL_UPDATE: 'RIVAL_UPDATE',
  RANKING_UPDATE: 'RANKING_UPDATE',
  GAME_STATUS: 'GAME_STATUS',
  ADMIN_ALERT: 'ADMIN_ALERT',
  TEAM_CHECKIN: 'TEAM_CHECKIN',
  TEAM_UPDATE: 'TEAM_UPDATE',
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
  gameId: string;
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

  /**
   * Connect as a team member
   */
  connectAsTeam(gameId: string, teamId: string): void {
    this.connect({ type: 'team', gameId, teamId });
  }

  /**
   * Connect as an admin
   */
  connectAsAdmin(gameId: string): void {
    this.connect({ type: 'admin', gameId });
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
      // Different config, disconnect first
      this.disconnect();
    }

    this.config = config;
    this.isIntentionallyClosed = false;
    this.createConnection();
  }

  private isSameConfig(config: ConnectionConfig): boolean {
    if (!this.config) return false;
    if (this.config.type !== config.type) return false;
    if (this.config.gameId !== config.gameId) return false;
    if (config.type === 'team' && this.config.type === 'team') {
      return this.config.teamId === config.teamId;
    }
    return true;
  }

  private buildUrl(): string {
    if (!this.config) {
      throw new Error('Connection config not set');
    }

    if (this.config.type === 'team') {
      const token = storage.getAuthToken();
      const baseUrl = `${WS_BASE_URL}/game/${this.config.gameId}/team/${this.config.teamId}`;
      return token ? `${baseUrl}?token=${encodeURIComponent(token)}` : baseUrl;
    } else {
      return `${WS_BASE_URL}/game/${this.config.gameId}/admin`;
    }
  }

  private createConnection(): void {
    if (!this.config) {
      console.error('Cannot connect: config not set');
      return;
    }

    const url = this.buildUrl();
    console.log('Connecting to WebSocket:', url);

    try {
      this.socket = new WebSocket(url);

      this.socket.onopen = () => {
        console.log('WebSocket connected as', this.config?.type);
        this.reconnectAttempts = 0;
        this.reconnectDelay = 1000;
        this.notifyConnectionState(true);
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
        this.socket = null;
        this.notifyConnectionState(false);

        if (!this.isIntentionallyClosed) {
          this.scheduleReconnect();
        }
      };
    } catch (error) {
      console.error('Failed to create WebSocket connection:', error);
      this.scheduleReconnect();
    }
  }

  private scheduleReconnect(): void {
    if (this.reconnectTimeout) {
      clearTimeout(this.reconnectTimeout);
    }

    if (this.reconnectAttempts >= this.maxReconnectAttempts) {
      console.error('Max reconnection attempts reached');
      return;
    }

    this.reconnectAttempts++;
    const delay = Math.min(this.reconnectDelay * this.reconnectAttempts, 30000);

    console.log(`Reconnecting in ${delay}ms (attempt ${this.reconnectAttempts})`);

    this.reconnectTimeout = window.setTimeout(() => {
      this.createConnection();
    }, delay);
  }

  private handleMessage(message: WebSocketMessage): void {
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
   */
  disconnect(): void {
    this.isIntentionallyClosed = true;

    if (this.reconnectTimeout) {
      clearTimeout(this.reconnectTimeout);
      this.reconnectTimeout = null;
    }

    if (this.socket) {
      this.socket.close();
      this.socket = null;
    }

    this.messageHandlers.clear();
    this.connectionStateHandlers.clear();
    this.config = null;
  }

  /**
   * Check if currently connected
   */
  isConnected(): boolean {
    return this.socket?.readyState === WebSocket.OPEN;
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
