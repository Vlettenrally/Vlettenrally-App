/**
 * useConnectionStatus Hook - Track WebSocket connection status with delay
 * 
 * Returns connection status with a short delay before showing "disconnected"
 * to avoid flickering on initial page load or brief reconnections.
 */
import { useEffect, useState } from 'react';
import { websocket } from '../services/websocket';

const DISCONNECT_DELAY_MS = 2000;

export function useConnectionStatus() {
  const [connected, setConnected] = useState(true); // Assume connected initially
  const [showDisconnected, setShowDisconnected] = useState(false);

  useEffect(() => {
    let disconnectTimer: ReturnType<typeof setTimeout> | null = null;

    const handleConnectionChange = (isConnected: boolean) => {
      setConnected(isConnected);

      if (isConnected) {
        // Clear any pending disconnect timer and immediately show connected
        if (disconnectTimer) {
          clearTimeout(disconnectTimer);
          disconnectTimer = null;
        }
        setShowDisconnected(false);
      } else {
        // Delay showing disconnected state to avoid flickering
        disconnectTimer = setTimeout(() => {
          setShowDisconnected(true);
        }, DISCONNECT_DELAY_MS);
      }
    };

    // Subscribe to connection changes
    const unsubscribe = websocket.onConnectionChange(handleConnectionChange);

    // Check current connection state
    const currentState = websocket.isConnected();
    if (!currentState) {
      // If already disconnected, start the delay timer
      disconnectTimer = setTimeout(() => {
        setShowDisconnected(true);
      }, DISCONNECT_DELAY_MS);
    }

    return () => {
      unsubscribe();
      if (disconnectTimer) {
        clearTimeout(disconnectTimer);
      }
    };
  }, []);

  return {
    /** Real-time connection status */
    connected,
    /** Whether to show the disconnected banner (with delay applied) */
    showDisconnected,
  };
}
