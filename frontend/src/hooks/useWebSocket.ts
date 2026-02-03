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
 */
export function useAdminWebSocket(gameId: string | null) {
  return useWebSocket({ type: 'admin', gameId });
}

/**
 * Generic WebSocket hook
 */
export function useWebSocket(options: UseWebSocketOptions) {
  const { type, gameId, teamId } = options;
  const isConnectedRef = useRef(false);
  const currentGameIdRef = useRef<string | null>(null);
  const [connected, setConnected] = useState(false);

  useEffect(() => {
    // For team connections, require both gameId and teamId
    if (type === 'team') {
      if (!gameId || !teamId) return;
      
      const config: ConnectionConfig = { type: 'team', gameId, teamId };
      
      if (!isConnectedRef.current) {
        websocket.connect(config);
        isConnectedRef.current = true;
        currentGameIdRef.current = gameId;
      }
    } else {
      // For admin connections, connect without gameId requirement
      // Connection is game-agnostic, we switch games via SWITCH_GAME message
      if (!isConnectedRef.current) {
        websocket.connectAsAdmin(gameId || undefined);
        isConnectedRef.current = true;
        currentGameIdRef.current = gameId;
      } else if (gameId && gameId !== currentGameIdRef.current) {
        // Already connected, just switch game
        websocket.switchGame(gameId);
        currentGameIdRef.current = gameId;
      }
    }

    // Subscribe to connection state changes
    const unsubscribe = websocket.onConnectionChange((isConnected) => {
      setConnected(isConnected);
    });

    return () => {
      unsubscribe();
    };
  }, [type, gameId, teamId]);

  // Cleanup on unmount
  useEffect(() => {
    return () => {
      websocket.disconnect();
      isConnectedRef.current = false;
      currentGameIdRef.current = null;
    };
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
