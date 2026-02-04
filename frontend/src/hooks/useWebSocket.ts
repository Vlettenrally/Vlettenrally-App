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
 * NOTE: Connection management is handled by AdminLayout, this hook only provides subscribe/send
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
      // For admin connections, connection is managed by AdminLayout
      // This hook only tracks the current gameId for switching
      if (gameId && gameId !== currentGameIdRef.current) {
        // Switch to the new game if connected
        if (websocket.isConnected() && websocket.getConnectionType() === 'admin') {
          websocket.switchGame(gameId);
        }
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

  // Cleanup on unmount - only disconnect team connections, admin is managed by layout
  useEffect(() => {
    return () => {
      if (type === 'team') {
        websocket.disconnect();
        isConnectedRef.current = false;
        currentGameIdRef.current = null;
      }
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
