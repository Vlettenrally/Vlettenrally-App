/**
 * Tests for the WebSocket service's reconnection logic.
 *
 * Focus: recovery after a mobile tab is backgrounded and foregrounded — the
 * scenario that produced false "server not reachable" banners. We drive a fake
 * WebSocket and fake timers so we can assert exactly when (and how often) a new
 * socket is opened.
 */
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';

// The service fetches a short-lived WS token before opening the socket. Mock the
// api module so buildUrl() resolves synchronously without loading the real client.
vi.mock('./api', () => ({
  default: {
    getTeamWebSocketToken: vi
      .fn()
      .mockResolvedValue({ data: { token: 'test-token', expiresInSeconds: 30 } }),
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
