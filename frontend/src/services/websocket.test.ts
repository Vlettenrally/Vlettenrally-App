/**
 * Tests for the WebSocket service's reconnection logic.
 *
 * Focus: recovery after a mobile tab is backgrounded and foregrounded — the
 * scenario that produced false "server not reachable" banners. We drive a fake
 * WebSocket and fake timers so we can assert exactly when (and how often) a new
 * socket is opened.
 */
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';

// Shared deferred token state captured by the mock-factory closure below.
// Because vi.resetModules() resets the module cache but NOT the test file's own
// scope, this object is shared between the factory (re-invoked on each fresh import)
// and the tests that control it. Set `pending = true` before a reconnect to stall
// the next token fetch; call `resolve?.()` to let it complete.
const tokenDeferred: { pending: boolean; resolve: (() => void) | null } = {
  pending: false,
  resolve: null,
};

// The service fetches a short-lived WS token before opening the socket. Mock the
// api module so buildUrl() resolves synchronously without loading the real client.
// Uses mockImplementation (not mockResolvedValue) so the tokenDeferred closure is
// live even after vi.resetModules() creates a fresh module instance.
vi.mock('./api', () => ({
  default: {
    getTeamWebSocketToken: vi.fn().mockImplementation(() => {
      if (tokenDeferred.pending) {
        tokenDeferred.pending = false; // Only defer the next call.
        return new Promise<{ data: { token: string } }>((res) => {
          tokenDeferred.resolve = () => res({ data: { token: 'test-token' } });
        });
      }
      return Promise.resolve({ data: { token: 'test-token', expiresInSeconds: 30 } });
    }),
    getAdminWebSocketToken: vi
      .fn()
      .mockResolvedValue({ data: { token: 'test-token', expiresInSeconds: 30 } }),
  },
}));

/** Minimal stand-in for the browser WebSocket; records instances and lets tests drive lifecycle. */
class FakeWebSocket {
  static instances: FakeWebSocket[] = [];
  static readonly CONNECTING = 0;
  static readonly OPEN = 1;
  static readonly CLOSING = 2;
  static readonly CLOSED = 3;

  readyState = FakeWebSocket.CONNECTING;
  url: string;
  sent: string[] = [];
  onopen: (() => void) | null = null;
  onclose: (() => void) | null = null;
  onmessage: ((event: { data: string }) => void) | null = null;
  onerror: ((event: unknown) => void) | null = null;

  constructor(url: string) {
    this.url = url;
    FakeWebSocket.instances.push(this);
  }

  send(data: string): void {
    this.sent.push(data);
  }

  close(): void {
    this.readyState = FakeWebSocket.CLOSED;
    this.onclose?.();
  }

  /** Test helper: simulate the server accepting the connection. */
  simulateOpen(): void {
    this.readyState = FakeWebSocket.OPEN;
    this.onopen?.();
  }

  /** Test helper: simulate the socket dropping (e.g. while backgrounded). */
  simulateDrop(): void {
    this.readyState = FakeWebSocket.CLOSED;
    this.onclose?.();
  }
}

type WebSocketService = typeof import('./websocket').websocket;

/** Fresh singleton per test, with its own window/document listeners. */
async function freshWebSocket(): Promise<WebSocketService> {
  vi.resetModules();
  const mod = await import('./websocket');
  return mod.websocket;
}

/** Flush pending microtasks (the awaited token fetch) without advancing the clock. */
async function flushMicrotasks(): Promise<void> {
  await vi.advanceTimersByTimeAsync(0);
}

function setVisibility(state: 'visible' | 'hidden'): void {
  Object.defineProperty(document, 'visibilityState', { value: state, configurable: true });
}

describe('WebSocket reconnection', () => {
  let ws: WebSocketService;

  beforeEach(() => {
    vi.useFakeTimers();
    FakeWebSocket.instances = [];
    vi.stubGlobal('WebSocket', FakeWebSocket);
    setVisibility('visible');
  });

  afterEach(() => {
    // Remove the singleton's window/document listeners so they don't leak across tests.
    ws?.destroy();
    vi.unstubAllGlobals();
    vi.useRealTimers();
  });

  /** Connect as a team and simulate the server accepting the socket. */
  async function connectAndOpen(): Promise<FakeWebSocket> {
    ws.connectAsTeam('game-1', 'team-1');
    await flushMicrotasks(); // resolve token fetch -> socket constructed
    const socket = FakeWebSocket.instances.at(-1)!;
    socket.simulateOpen();
    return socket;
  }

  it('reconnects after an unintentional drop using the backoff timer', async () => {
    ws = await freshWebSocket();
    await connectAndOpen();
    expect(FakeWebSocket.instances).toHaveLength(1);

    FakeWebSocket.instances[0].simulateDrop();
    // First reconnect attempt is scheduled 1s out; advancing the clock fires it.
    await vi.advanceTimersByTimeAsync(1000);

    expect(FakeWebSocket.instances).toHaveLength(2);
  });

  it('keeps retrying past the max-attempt cap instead of giving up forever', async () => {
    ws = await freshWebSocket();
    await connectAndOpen();

    // Fail every reconnect. The capped backoff is at most 10s, so advancing 10s
    // per cycle fires each scheduled attempt. Old behaviour stopped after 10
    // attempts (~11 sockets total); the fix must keep opening new sockets.
    for (let i = 0; i < 15; i++) {
      FakeWebSocket.instances.at(-1)!.simulateDrop();
      await vi.advanceTimersByTimeAsync(10000);
    }

    expect(FakeWebSocket.instances.length).toBeGreaterThan(11);
  });

  it('foregrounding reconnects immediately without waiting for the backoff delay', async () => {
    ws = await freshWebSocket();
    await connectAndOpen();

    FakeWebSocket.instances[0].simulateDrop();
    const countAfterDrop = FakeWebSocket.instances.length;

    // Foreground the app. No clock advance — only the visibility handler should
    // open a socket; the pending 1s backoff timer must not have fired yet.
    setVisibility('visible');
    document.dispatchEvent(new Event('visibilitychange'));
    await flushMicrotasks();

    expect(FakeWebSocket.instances.length).toBe(countAfterDrop + 1);
  });

  it('does not open duplicate sockets when online and visibility fire together', async () => {
    ws = await freshWebSocket();
    await connectAndOpen();

    FakeWebSocket.instances[0].simulateDrop();
    const countAfterDrop = FakeWebSocket.instances.length;

    // Both recovery triggers fire in the same tick. The in-flight guard must let
    // only one connection proceed while the first one's token fetch is pending.
    window.dispatchEvent(new Event('online'));
    document.dispatchEvent(new Event('visibilitychange'));
    await flushMicrotasks();

    expect(FakeWebSocket.instances.length).toBe(countAfterDrop + 1);
  });

  it('does not reconnect while the page stays hidden', async () => {
    ws = await freshWebSocket();
    await connectAndOpen();

    FakeWebSocket.instances[0].simulateDrop();
    const countAfterDrop = FakeWebSocket.instances.length;

    // A hidden -> still-hidden visibilitychange must be ignored.
    setVisibility('hidden');
    document.dispatchEvent(new Event('visibilitychange'));
    await flushMicrotasks();

    expect(FakeWebSocket.instances.length).toBe(countAfterDrop);
  });
});

/**
 * Tests for the three specific deadlock bugs fixed by the generation counter.
 *
 * These tests need a "slow" token fetch so they can observe whether recovery
 * events bypass a stuck isConnecting flag. The tokenDeferred object is captured
 * in the vi.mock() factory closure above and survives vi.resetModules(), so
 * setting tokenDeferred.pending = true stalls the next token fetch for any
 * fresh websocket instance.
 */
describe('reconnection deadlock: generation counter', () => {
  let ws: WebSocketService;

  beforeEach(async () => {
    vi.useFakeTimers();
    FakeWebSocket.instances = [];
    vi.stubGlobal('WebSocket', FakeWebSocket);
    setVisibility('visible');
    tokenDeferred.pending = false;
    tokenDeferred.resolve = null;
    ws = await freshWebSocket();
  });

  afterEach(() => {
    ws?.destroy();
    vi.unstubAllGlobals();
    vi.useRealTimers();
  });

  async function connectAndOpen(): Promise<FakeWebSocket> {
    ws.connectAsTeam('game-1', 'team-1');
    await flushMicrotasks();
    const socket = FakeWebSocket.instances.at(-1)!;
    socket.simulateOpen();
    return socket;
  }

  it('foregrounding reconnects even when a prior token fetch is stuck in-flight', async () => {
    await connectAndOpen();
    FakeWebSocket.instances[0].simulateDrop();

    // Next token fetch will hang until tokenDeferred.resolve() is called.
    tokenDeferred.pending = true;

    // Fire the 1 s backoff timer → createConnection starts, isConnecting = true, stuck.
    await vi.advanceTimersByTimeAsync(1000);
    expect(FakeWebSocket.instances).toHaveLength(1); // no new socket yet

    // User tabs back in. handleVisibilityChange now calls forceReconnect() which
    // clears isConnecting and increments the generation before starting a fresh attempt.
    setVisibility('visible');
    document.dispatchEvent(new Event('visibilitychange'));
    await flushMicrotasks(); // fresh (non-deferred) token resolves → socket2 opened

    expect(FakeWebSocket.instances).toHaveLength(2); // reconnected

    // The stale token eventually resolves. The generation check must discard it.
    tokenDeferred.resolve?.();
    await flushMicrotasks();
    expect(FakeWebSocket.instances).toHaveLength(2); // no orphaned socket3
  });

  it('online event reconnects even when a prior token fetch is stuck in-flight', async () => {
    await connectAndOpen();

    window.dispatchEvent(new Event('offline'));
    FakeWebSocket.instances[0].simulateDrop();

    // Next token fetch (on first online) will hang.
    tokenDeferred.pending = true;

    // Network restores → stale token fetch starts, isConnecting = true.
    window.dispatchEvent(new Event('online'));
    expect(FakeWebSocket.instances).toHaveLength(1); // no socket yet

    // A second online event (e.g. network switch). handleOnline now clears isConnecting
    // and increments the generation before creating a fresh connection, so it is not
    // blocked by the stuck flag from the first event.
    window.dispatchEvent(new Event('online'));
    await flushMicrotasks(); // fresh token resolves → socket2 opened

    expect(FakeWebSocket.instances).toHaveLength(2); // reconnected

    // Stale token resolves — generation check discards it.
    tokenDeferred.resolve?.();
    await flushMicrotasks();
    expect(FakeWebSocket.instances).toHaveLength(2); // no orphaned socket3
  });

  it('stale createConnection() from a page-navigation disconnect is discarded', async () => {
    await connectAndOpen();
    FakeWebSocket.instances[0].simulateDrop();

    // Backoff reconnect's token fetch will hang (simulates slow network on reconnect).
    tokenDeferred.pending = true;

    // Fire the backoff timer → createConnection starts with the slow token fetch.
    await vi.advanceTimersByTimeAsync(1000);
    expect(FakeWebSocket.instances).toHaveLength(1); // token still pending

    // Page navigation: old page unmounts (disconnect), new page mounts (connect).
    // disconnect() increments the generation so the stale continuation is invalidated.
    ws.disconnect();
    ws.connectAsTeam('game-1', 'team-1');
    await flushMicrotasks(); // fresh token resolves → socket2 opened for new page

    expect(FakeWebSocket.instances).toHaveLength(2);

    // Stale token finally resolves — must be discarded, not race to create socket3.
    tokenDeferred.resolve?.();
    await flushMicrotasks();
    expect(FakeWebSocket.instances).toHaveLength(2); // no orphaned socket3
  });
});
