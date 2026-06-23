/**
 * Tests for the token-refresh state machine in api.ts: proactive refresh of a
 * soon-to-expire token, reactive refresh-and-retry on 401, concurrent-refresh
 * deduplication, and the safety guards (redirect on failure, no double retry,
 * no retry when the token didn't actually change).
 */
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { makeJwt, expSecondsFromNow } from '../test/jwt';

// Mutable in-memory token store backing the storage mock.
const tokens = { access: null as string | null, refresh: null as string | null };
const storageMock = {
  getAuthToken: vi.fn(() => tokens.access),
  setAuthToken: vi.fn((t: string) => {
    tokens.access = t;
  }),
  getRefreshToken: vi.fn(() => tokens.refresh),
  setRefreshToken: vi.fn((t: string) => {
    tokens.refresh = t;
  }),
  getUserRole: vi.fn(() => 'team' as const),
  clearSession: vi.fn(),
};
vi.mock('./storage', () => ({ default: storageMock }));

type FetchResponse = { ok: boolean; status: number; json: () => Promise<unknown> };
function jsonResponse(status: number, body: unknown): FetchResponse {
  return { ok: status >= 200 && status < 300, status, json: async () => body };
}

const REFRESH_URL = /\/auth\/refresh$/;

/** Fresh ApiService singleton per test (clears internal isRefreshing/cached state). */
async function loadApi() {
  vi.resetModules();
  return (await import('./api')).default;
}

let fetchMock: ReturnType<typeof vi.fn>;

beforeEach(() => {
  tokens.access = null;
  tokens.refresh = null;
  vi.clearAllMocks();
  fetchMock = vi.fn();
  vi.stubGlobal('fetch', fetchMock);
  // jsdom won't navigate; replace location so redirectToLogin can set href.
  Object.defineProperty(window, 'location', {
    writable: true,
    configurable: true,
    value: { href: '' },
  });
});

afterEach(() => {
  vi.unstubAllGlobals();
});

function refreshCallCount(): number {
  return fetchMock.mock.calls.filter(([url]) => REFRESH_URL.test(String(url))).length;
}

describe('proactive refresh', () => {
  it('refreshes a soon-to-expire token before issuing the request', async () => {
    tokens.access = makeJwt({ exp: expSecondsFromNow(60) }); // within 5-min window
    tokens.refresh = 'refresh-1';
    const newToken = makeJwt({ exp: expSecondsFromNow(3600) });

    fetchMock.mockImplementation(async (url: string) => {
      if (REFRESH_URL.test(url)) {
        return jsonResponse(200, { accessToken: newToken, refreshToken: 'refresh-2' });
      }
      return jsonResponse(200, { ok: true });
    });

    const api = await loadApi();
    const result = await api.get('/games');

    expect(refreshCallCount()).toBe(1);
    expect(result.data).toEqual({ ok: true });
    // The follow-up request carries the refreshed token.
    const gamesCall = fetchMock.mock.calls.find(([url]) => String(url).endsWith('/games'))!;
    expect((gamesCall[1] as RequestInit).headers).toMatchObject({
      Authorization: `Bearer ${newToken}`,
    });
  });

  it('does not refresh a token that is not expiring soon', async () => {
    tokens.access = makeJwt({ exp: expSecondsFromNow(3600) });
    tokens.refresh = 'refresh-1';
    fetchMock.mockResolvedValue(jsonResponse(200, { ok: true }));

    const api = await loadApi();
    await api.get('/games');

    expect(refreshCallCount()).toBe(0);
  });
});

describe('reactive refresh on 401', () => {
  it('refreshes and retries the original request once', async () => {
    tokens.access = makeJwt({ exp: expSecondsFromNow(3600) }); // not expiring -> no proactive
    tokens.refresh = 'refresh-1';
    const newToken = makeJwt({ exp: expSecondsFromNow(3600), sub: 'rotated' });

    let gamesCalls = 0;
    fetchMock.mockImplementation(async (url: string) => {
      if (REFRESH_URL.test(url)) {
        return jsonResponse(200, { accessToken: newToken, refreshToken: 'refresh-2' });
      }
      gamesCalls += 1;
      return gamesCalls === 1 ? jsonResponse(401, { message: 'expired' }) : jsonResponse(200, { ok: true });
    });

    const api = await loadApi();
    const result = await api.get('/games');

    expect(refreshCallCount()).toBe(1);
    expect(gamesCalls).toBe(2); // initial 401 + retry
    expect(result.data).toEqual({ ok: true });
  });

  it('redirects to login when the refresh itself fails', async () => {
    tokens.access = makeJwt({ exp: expSecondsFromNow(3600) });
    tokens.refresh = 'refresh-1';
    fetchMock.mockImplementation(async (url: string) => {
      if (REFRESH_URL.test(url)) return jsonResponse(500, {});
      return jsonResponse(401, { message: 'expired' });
    });

    const api = await loadApi();
    const result = await api.get('/games');

    expect(result.status).toBe(401);
    expect(storageMock.clearSession).toHaveBeenCalled();
    expect(window.location.href).toBe('/check-in'); // role 'team'
  });

  it('does not retry again when the retried request also 401s', async () => {
    tokens.access = makeJwt({ exp: expSecondsFromNow(3600) });
    tokens.refresh = 'refresh-1';
    const newToken = makeJwt({ exp: expSecondsFromNow(3600), sub: 'rotated' });
    fetchMock.mockImplementation(async (url: string) => {
      if (REFRESH_URL.test(url)) {
        return jsonResponse(200, { accessToken: newToken, refreshToken: 'refresh-2' });
      }
      return jsonResponse(401, { message: 'still nope' });
    });

    const api = await loadApi();
    const result = await api.get('/games');

    // initial GET + 1 refresh + 1 retry GET = 3, and no further attempts
    expect(fetchMock).toHaveBeenCalledTimes(3);
    expect(result.status).toBe(401);
    expect(result.error).toBe('still nope');
  });

  it('does not retry when refresh returns the same (unchanged) token', async () => {
    const sameToken = makeJwt({ exp: expSecondsFromNow(3600) });
    tokens.access = sameToken;
    tokens.refresh = 'refresh-1';
    fetchMock.mockImplementation(async (url: string) => {
      if (REFRESH_URL.test(url)) {
        return jsonResponse(200, { accessToken: sameToken, refreshToken: 'refresh-2' });
      }
      return jsonResponse(401, { message: 'expired' });
    });

    const api = await loadApi();
    const result = await api.get('/games');

    // initial GET + refresh only; the unchanged-token guard prevents a retry
    expect(fetchMock).toHaveBeenCalledTimes(2);
    expect(result.error).toBe('Authentication failed');
  });
});

describe('concurrent refresh deduplication', () => {
  it('shares a single /auth/refresh across concurrent requests', async () => {
    tokens.access = makeJwt({ exp: expSecondsFromNow(60) }); // expiring -> proactive refresh
    tokens.refresh = 'refresh-1';
    const newToken = makeJwt({ exp: expSecondsFromNow(3600) });

    fetchMock.mockImplementation(async (url: string) => {
      if (REFRESH_URL.test(url)) {
        return jsonResponse(200, { accessToken: newToken, refreshToken: 'refresh-2' });
      }
      return jsonResponse(200, { ok: true });
    });

    const api = await loadApi();
    const [r1, r2] = await Promise.all([api.get('/games'), api.get('/teams')]);

    expect(refreshCallCount()).toBe(1); // deduped
    expect(r1.data).toEqual({ ok: true });
    expect(r2.data).toEqual({ ok: true });
  });
});
