/**
 * useAdminSync - Re-syncs admin state when the app resumes or reconnects.
 *
 * The admin shell, its pages and the sidebar widgets each fetch their own data
 * once on mount (keyed to the selected game) and then rely on incremental
 * WebSocket events. While the app is offline or suspended (screen locked, tab
 * hidden) the socket drops and any events fired during the gap are missed; the
 * socket reconnects but nothing refetches, so data stays stale until a manual
 * refresh or remount.
 *
 * Unlike the player (whose state lives in central Zustand stores, so useTeamSync
 * can refetch everything from one place), admin state is distributed across
 * per-page/per-widget local state. So this is split in two:
 *
 *   - useAdminSync()          owns the triggers + throttle and bumps a counter,
 *   - useAdminResync(fn)      lets each surface re-run its own fetch on a bump.
 *
 * useAdminSync is mounted once in AdminLayout so it runs for the whole admin
 * app. The triggers mirror useTeamSync: a disconnected -> connected WebSocket
 * transition, the page becoming visible again, or the browser firing 'online'.
 * It is event-driven, not a standing poll loop.
 */
import { useEffect, useRef } from 'react';
import { create } from 'zustand';
import { websocket } from '../services/websocket';
import { useLatestRef } from './useLatestRef';

interface AdminSyncState {
  /** Monotonic counter; bumped once per (throttled) resync trigger. */
  nonce: number;
  bump: () => void;
}

/** Shared resync signal. Consumers subscribe via useAdminResync. */
export const useAdminSyncStore = create<AdminSyncState>((set) => ({
  nonce: 0,
  bump: () => set((s) => ({ nonce: s.nonce + 1 })),
}));

/** Skip a resync if one fired within this window (collapses duplicate triggers). */
const RESYNC_THROTTLE_MS = 5000;

/**
 * Trigger owner. Mount once (in AdminLayout). Bumps the shared resync nonce on
 * reconnect / page-visible / online, throttled and guarded against offline.
 */
export function useAdminSync() {
  const lastSyncAt = useRef(0);

  useEffect(() => {
    const triggerResync = () => {
      // Offline: a refetch would just serve stale cache (or fail). Wait for the
      // 'online'/reconnect trigger instead.
      if (typeof navigator !== 'undefined' && !navigator.onLine) return;
      if (Date.now() - lastSyncAt.current < RESYNC_THROTTLE_MS) return;
      lastSyncAt.current = Date.now();
      useAdminSyncStore.getState().bump();
    };

    // Reconnect trigger: fire only on a disconnected -> connected transition, so
    // the initial connect after mount (already covered by mount fetches) is skipped.
    let wasDisconnected = false;
    const unsubscribe = websocket.onConnectionChange((connected) => {
      if (!connected) {
        wasDisconnected = true;
      } else if (wasDisconnected) {
        wasDisconnected = false;
        triggerResync();
      }
    });

    // Resume triggers: page becomes visible again, or the browser comes online.
    const handleVisibility = () => {
      if (!document.hidden) triggerResync();
    };
    const handleOnline = () => {
      triggerResync();
    };
    document.addEventListener('visibilitychange', handleVisibility);
    window.addEventListener('online', handleOnline);

    return () => {
      unsubscribe();
      document.removeEventListener('visibilitychange', handleVisibility);
      window.removeEventListener('online', handleOnline);
    };
  }, []);
}

/**
 * Consumer helper. Calls onResync whenever a resync is triggered (not on initial
 * mount). onResync is held in a ref, so callers don't need a stable reference.
 */
export function useAdminResync(onResync: () => void) {
  const callbackRef = useLatestRef(onResync);

  const seenNonce = useRef(useAdminSyncStore.getState().nonce);

  useEffect(() => {
    return useAdminSyncStore.subscribe((state) => {
      if (state.nonce === seenNonce.current) return;
      seenNonce.current = state.nonce;
      callbackRef.current();
    });
  }, [callbackRef]);
}

export default useAdminSync;
