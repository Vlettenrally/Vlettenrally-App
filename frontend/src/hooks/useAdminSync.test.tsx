/**
 * Tests for useAdminSync: a throttled, guarded resync signal triggered by
 * reconnect / page-visible / online. useAdminSync() owns the triggers and bumps
 * a shared nonce; useAdminResync(fn) runs fn on each bump (not on mount). We
 * mount both together and count callback invocations as a proxy for "a resync
 * ran".
 */
import { afterEach, beforeEach, expect, it, vi } from 'vitest';
import { cleanup, renderHook } from '@testing-library/react';

// Controllable websocket connection-state listener registry.
const wsSync = vi.hoisted(() => {
  const listeners = new Set<(connected: boolean) => void>();
  return {
    listeners,
    websocket: {
      onConnectionChange: (cb: (c: boolean) => void) => {
        listeners.add(cb);
        return () => listeners.delete(cb);
      },
    },
  };
});
vi.mock('../services/websocket', () => ({ websocket: wsSync.websocket }));

import { useAdminSync, useAdminResync, useAdminSyncStore } from './useAdminSync';

const flush = async () => {
  for (let i = 0; i < 10; i++) await Promise.resolve();
};

function fireConnection(connected: boolean) {
  wsSync.listeners.forEach((cb) => cb(connected));
}

function setOnline(online: boolean) {
  Object.defineProperty(navigator, 'onLine', { configurable: true, value: online });
}

beforeEach(() => {
  vi.useFakeTimers();
  vi.setSystemTime(new Date('2026-06-24T12:00:00.000Z'));
  vi.clearAllMocks();
  wsSync.listeners.clear();
  useAdminSyncStore.setState({ nonce: 0 });
  setOnline(true);
});

afterEach(() => {
  cleanup();
  vi.useRealTimers();
});

it('resyncs on a disconnected -> connected transition, but not on the initial connect', async () => {
  const onResync = vi.fn();
  renderHook(() => {
    useAdminSync();
    useAdminResync(onResync);
  });

  // Initial connect (no preceding disconnect) must not resync.
  fireConnection(true);
  await flush();
  expect(onResync).not.toHaveBeenCalled();

  // A real reconnect (disconnected -> connected) resyncs.
  fireConnection(false);
  fireConnection(true);
  await flush();
  expect(onResync).toHaveBeenCalledTimes(1);
});

it('throttles repeat triggers within the 5s window, then allows one after it', async () => {
  const onResync = vi.fn();
  renderHook(() => {
    useAdminSync();
    useAdminResync(onResync);
  });

  fireConnection(false);
  fireConnection(true);
  await flush();
  expect(onResync).toHaveBeenCalledTimes(1);

  // Within 5s -> skipped by the throttle.
  document.dispatchEvent(new Event('visibilitychange'));
  await flush();
  expect(onResync).toHaveBeenCalledTimes(1);

  // After the window -> allowed.
  vi.advanceTimersByTime(5001);
  document.dispatchEvent(new Event('visibilitychange'));
  await flush();
  expect(onResync).toHaveBeenCalledTimes(2);
});

it('does not resync while offline', async () => {
  setOnline(false);
  const onResync = vi.fn();
  renderHook(() => {
    useAdminSync();
    useAdminResync(onResync);
  });

  fireConnection(false);
  fireConnection(true);
  await flush();

  expect(onResync).not.toHaveBeenCalled();
});

it('resyncs when the browser fires online', async () => {
  const onResync = vi.fn();
  renderHook(() => {
    useAdminSync();
    useAdminResync(onResync);
  });

  window.dispatchEvent(new Event('online'));
  await flush();
  expect(onResync).toHaveBeenCalledTimes(1);
});
