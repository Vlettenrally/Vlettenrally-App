/**
 * useWebSocket Hook - React hook for WebSocket connection
 * 
 * Supports both team and admin connections
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
  /** Game ID to connect to */
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
  const [connected, setConnected] = useState(false);

  useEffect(() => {
    // Validate required parameters
    if (!gameId) return;
    if (type === 'team' && !teamId) return;

    // Build connection config
    const config: ConnectionConfig = type === 'team'
      ? { type: 'team', gameId, teamId: teamId! }
      : { type: 'admin', gameId };

    if (!isConnectedRef.current) {
      websocket.connect(config);
      isConnectedRef.current = true;
    }

    // Subscribe to connection state changes
    const unsubscribe = websocket.onConnectionChange((isConnected) => {
      setConnected(isConnected);
    });

    return () => {
      unsubscribe();
      websocket.disconnect();
      isConnectedRef.current = false;
      setConnected(false);
    };
  }, [type, gameId, teamId]);

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
  };
}
