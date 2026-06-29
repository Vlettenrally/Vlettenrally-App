/**
 * useWebSocket Hook - React hook for WebSocket connection
 * 
 * Supports both team and admin connections.
 * Admin connections are game-agnostic and use switchGame to change games without reconnecting.
 */
import { useEffect, useRef, useState, useCallback } from 'react';
import { 
  websocket, 
  WebSocketEventType, 
  type WebSocketMessage,
  type ConnectionConfig,
  type ConnectionType
} from '../services/websocket';

type MessageHandler = (message: WebSocketMessage) => void;

export interface UseWebSocketOptions {
  /** Connection type: 'team' or 'admin' */
  type: ConnectionType;
  /** Game ID to connect to (optional for admin - can switch later) */
  gameId: string | null;
  /** Team ID (required for team connections) */
  teamId?: string | null;
}

/**
 * Hook for team WebSocket connections
 */
export function useTeamWebSocket(gameId: string | null, teamId: string | null) {
  return useWebSocket({ type: 'team', gameId, teamId });
}

/**
 * Hook for admin WebSocket connections
 * Admin connections are game-agnostic - gameId can be null initially and switched later
 * Manages connection lifecycle: connects on mount, disconnects on unmount
 */
export function useAdminWebSocket(gameId: string | null) {
  return useWebSocket({ type: 'admin', gameId });
}

/**
 * Generic WebSocket hook
 */
export function useWebSocket(options: UseWebSocketOptions) {
  const { type, gameId, teamId } = options;
  const currentGameIdRef = useRef<string | null>(null);
  const currentTeamIdRef = useRef<string | null>(null);
  const [connected, setConnected] = useState(() => websocket.isConnected());

  // Subscribe to connection state changes (always, regardless of connection status)
  useEffect(() => {
    const unsubscribe = websocket.onConnectionChange((isConnected) => {
      setConnected(isConnected);
    });

    return () => {
      unsubscribe();
    };
  }, []);

  // Handle connection logic
  useEffect(() => {
    if (type === 'team') {
      // For team connections, require both gameId and teamId
      if (!gameId || !teamId) return;
      
      // Connect if not connected or if team/game changed
      const needsConnection = !websocket.isConnected() ||
        currentGameIdRef.current !== gameId ||
        currentTeamIdRef.current !== teamId;

      if (needsConnection) {
        const config: ConnectionConfig = { type: 'team', gameId, teamId };
        websocket.connect(config);
        currentGameIdRef.current = gameId;
        currentTeamIdRef.current = teamId;
      }
    } else {
      // For admin connections, connect once on mount
      if (!websocket.isConnected() && websocket.getConnectionType() !== 'admin') {
        websocket.connectAsAdmin();
      }
      
      // Switch game when gameId changes
      if (gameId && gameId !== currentGameIdRef.current) {
        websocket.switchGame(gameId);
        currentGameIdRef.current = gameId;
      }
    }
  }, [type, gameId, teamId]);

  // Cleanup on unmount - disconnect for both team and admin connections
  useEffect(() => {
    return () => {
      websocket.disconnect();
      currentGameIdRef.current = null;
      currentTeamIdRef.current = null;
    };
  }, [type]);

  const subscribe = useCallback((
    eventType: keyof typeof WebSocketEventType | 'all',
    handler: MessageHandler
  ) => {
    const type = eventType === 'all' 
      ? 'all' 
      : WebSocketEventType[eventType as keyof typeof WebSocketEventType];
    return websocket.on(type || eventType, handler);
  }, []);

  const send = useCallback((message: unknown) => {
    websocket.send(message);
  }, []);

  const isConnected = useCallback(() => {
    return websocket.isConnected();
  }, []);

  const getConnectionType = useCallback(() => {
    return websocket.getConnectionType();
  }, []);

  const getGameId = useCallback(() => {
    return websocket.getGameId();
  }, []);

  const switchGame = useCallback((newGameId: string) => {
    websocket.switchGame(newGameId);
    currentGameIdRef.current = newGameId;
  }, []);

  const getTeamId = useCallback(() => {
    return websocket.getTeamId();
  }, []);

  return {
    subscribe,
    send,
    isConnected,
    connected,
    getConnectionType,
    getGameId,
    getTeamId,
    switchGame,
  };
}

/**
 * Subscribe to WebSocket events and connection state without affecting the
 * connection lifecycle. Use in page components that live inside a layout
 * that already manages the connection (e.g. TeamLayout, AdminLayout).
 */
export function useWebSocketSubscription() {
  const [connected, setConnected] = useState(() => websocket.isConnected());

  useEffect(() => {
    return websocket.onConnectionChange(setConnected);
  }, []);

  const subscribe = useCallback((
    eventType: keyof typeof WebSocketEventType | 'all',
    handler: MessageHandler
  ) => {
    const type = eventType === 'all'
      ? 'all'
      : WebSocketEventType[eventType as keyof typeof WebSocketEventType];
    return websocket.on(type || eventType, handler);
  }, []);

  const send = useCallback((message: unknown) => {
    websocket.send(message);
  }, []);

  return { subscribe, send, connected };
}
