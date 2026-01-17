/**
 * useWebSocket Hook - React hook for WebSocket connection
 */
import { useEffect, useRef } from 'react';
import { websocket, WebSocketEventType, type WebSocketMessage } from '../services/websocket';

type MessageHandler = (message: WebSocketMessage) => void;

export function useWebSocket(gameId: string | null) {
  const isConnectedRef = useRef(false);

  useEffect(() => {
    if (!gameId) return;

    if (!isConnectedRef.current) {
      websocket.connect(gameId);
      isConnectedRef.current = true;
    }

    return () => {
      websocket.disconnect();
      isConnectedRef.current = false;
    };
  }, [gameId]);

  const subscribe = (
    type: keyof typeof WebSocketEventType | 'all',
    handler: MessageHandler
  ) => {
    return websocket.on(WebSocketEventType[type as keyof typeof WebSocketEventType] || type as any, handler);
  };

  const send = (message: unknown) => {
    websocket.send(message);
  };

  const isConnected = () => {
    return websocket.isConnected();
  };

  return {
    subscribe,
    send,
    isConnected,
  };
}
