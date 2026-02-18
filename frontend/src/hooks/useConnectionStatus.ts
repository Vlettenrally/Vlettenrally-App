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

    const clearDisconnectTimer = () => {
      if (disconnectTimer) {
        clearTimeout(disconnectTimer);
        disconnectTimer = null;
      }
    };

    const startDisconnectTimer = () => {
      // Only start a new timer if one isn't already running
      if (!disconnectTimer) {
        disconnectTimer = setTimeout(() => {
          disconnectTimer = null; // Clear reference when timer fires
          setShowDisconnected(true);
        }, DISCONNECT_DELAY_MS);
      }
    };

    const handleConnectionChange = (isConnected: boolean) => {
      setConnected(isConnected);

      if (isConnected) {
        // Clear any pending disconnect timer and immediately show connected
        clearDisconnectTimer();
        setShowDisconnected(false);
      } else {
        // Delay showing disconnected state to avoid flickering
        startDisconnectTimer();
      }
    };

    // Subscribe to connection changes
    const unsubscribe = websocket.onConnectionChange(handleConnectionChange);

    // Check current connection state and sync initial state
    const currentState = websocket.isConnected();
    setConnected(currentState);

    if (!currentState) {
      // If already disconnected, start the delay timer
      startDisconnectTimer();
    }

    return () => {
      unsubscribe();
      clearDisconnectTimer();
    };
  }, []);

  return {
    /** Real-time connection status */
    connected,
    /** Whether to show the disconnected banner (with delay applied) */
    showDisconnected,
  };
}
