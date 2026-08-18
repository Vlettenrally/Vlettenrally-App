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

  it('redirects to login when the refresh token is rejected (401)', async () => {
    tokens.access = makeJwt({ exp: expSecondsFromNow(3600) });
    tokens.refresh = 'refresh-1';
    fetchMock.mockImplementation(async (url: string) => {
      if (REFRESH_URL.test(url)) return jsonResponse(401, { message: 'invalid refresh token' });
      return jsonResponse(401, { message: 'expired' });
    });

    const api = await loadApi();
    const result = await api.get('/games');

    expect(result.status).toBe(401);
    expect(storageMock.clearSession).toHaveBeenCalled();
    expect(window.location.href).toBe('/check-in'); // role 'team'
  });

  it('does NOT clear the session on a transient refresh failure (5xx)', async () => {
    // A flaky-network / server-blip refresh failure must never log a team out; the tokens
    // are kept so a later request can retry.
    tokens.access = makeJwt({ exp: expSecondsFromNow(3600) });
    tokens.refresh = 'refresh-1';
    fetchMock.mockImplementation(async (url: string) => {
      if (REFRESH_URL.test(url)) return jsonResponse(502, {});
      return jsonResponse(401, { message: 'expired' });
    });

    const api = await loadApi();
    const result = await api.get('/games');

    expect(storageMock.clearSession).not.toHaveBeenCalled();
    expect(window.location.href).toBe(''); // no redirect
    expect(tokens.refresh).toBe('refresh-1'); // token preserved
    expect(result.status).toBe(401);
  });

  it('retries once when the backend defers the refresh (503) and succeeds', async () => {
    // 503 means the backend could not resolve the rotation yet — typically a duplicate refresh
    // in flight. That used to be a 401, which every client reads as "your session is over".
    vi.useFakeTimers();
    try {
      tokens.access = makeJwt({ exp: expSecondsFromNow(3600) });
      tokens.refresh = 'refresh-1';
      const rotated = makeJwt({ exp: expSecondsFromNow(3600), sub: 'rotated' });
      let refreshCalls = 0;
      let gets = 0;
      fetchMock.mockImplementation(async (url: string) => {
        if (REFRESH_URL.test(url)) {
          refreshCalls += 1;
          return refreshCalls === 1
            ? jsonResponse(503, { message: 'retry' })
            : jsonResponse(200, { accessToken: rotated, refreshToken: 'refresh-2' });
        }
        gets += 1;
        // The first GET 401s, which is what drives the reactive refresh.
        return gets === 1 ? jsonResponse(401, { message: 'expired' }) : jsonResponse(200, []);
      });

      const api = await loadApi();
      const pending = api.get('/games');
      await vi.advanceTimersByTimeAsync(2500);
      await pending;

      expect(refreshCalls).toBe(2);
      expect(tokens.refresh).toBe('refresh-2');
      expect(storageMock.clearSession).not.toHaveBeenCalled();
    } finally {
      vi.useRealTimers();
    }
  });

  it('keeps the session when both 503 attempts fail', async () => {
    vi.useFakeTimers();
    try {
      tokens.access = makeJwt({ exp: expSecondsFromNow(3600) });
      tokens.refresh = 'refresh-1';
      fetchMock.mockImplementation(async (url: string) => {
        if (REFRESH_URL.test(url)) return jsonResponse(503, {});
        return jsonResponse(401, { message: 'expired' });
      });

      const api = await loadApi();
      const pending = api.get('/games');
      await vi.advanceTimersByTimeAsync(2500);
      await pending;

      expect(refreshCallCount()).toBe(2); // one retry, not a loop
      expect(tokens.refresh).toBe('refresh-1');
      expect(storageMock.clearSession).not.toHaveBeenCalled();
      expect(window.location.href).toBe('');
    } finally {
      vi.useRealTimers();
    }
  });

  it('does NOT clear the session when the refresh request throws (network error)', async () => {
    tokens.access = makeJwt({ exp: expSecondsFromNow(3600) });
    tokens.refresh = 'refresh-1';
    fetchMock.mockImplementation(async (url: string) => {
      if (REFRESH_URL.test(url)) throw new TypeError('Failed to fetch');
      return jsonResponse(401, { message: 'expired' });
    });

    const api = await loadApi();
    await api.get('/games');

    expect(storageMock.clearSession).not.toHaveBeenCalled();
    expect(tokens.refresh).toBe('refresh-1');
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

describe('cross-tab refresh coordination', () => {
  it('serialises refresh through the Web Locks API when available', async () => {
    tokens.access = makeJwt({ exp: expSecondsFromNow(60) }); // expiring -> proactive refresh
    tokens.refresh = 'refresh-1';
    const newToken = makeJwt({ exp: expSecondsFromNow(3600) });

    const locksMock = {
      request: vi.fn(async (_name: string, cb: () => Promise<unknown>) => cb()),
    };
    vi.stubGlobal('navigator', { locks: locksMock });

    fetchMock.mockImplementation(async (url: string) => {
      if (REFRESH_URL.test(url)) {
        return jsonResponse(200, { accessToken: newToken, refreshToken: 'refresh-2' });
      }
      return jsonResponse(200, { ok: true });
    });

    const api = await loadApi();
    const result = await api.get('/games');

    expect(locksMock.request).toHaveBeenCalledWith('vlettenrally-token-refresh', expect.any(Function));
    expect(refreshCallCount()).toBe(1);
    expect(result.data).toEqual({ ok: true });
  });

  it('reuses a token another tab already rotated instead of calling /auth/refresh', async () => {
    const expiring = makeJwt({ exp: expSecondsFromNow(60) });
    const fresh = makeJwt({ exp: expSecondsFromNow(3600) });
    tokens.access = expiring;
    tokens.refresh = 'refresh-1';

    // Another tab rotates the stored token the instant we acquire the refresh lock.
    const locksMock = {
      request: vi.fn(async (_name: string, cb: () => Promise<unknown>) => {
        tokens.access = fresh;
        return cb();
      }),
    };
    vi.stubGlobal('navigator', { locks: locksMock });

    fetchMock.mockImplementation(async () => jsonResponse(200, { ok: true }));

    const api = await loadApi();
    const result = await api.get('/games');

    expect(refreshCallCount()).toBe(0); // reused the other tab's fresh token, no network refresh
    expect(result.data).toEqual({ ok: true });
    const gamesCall = fetchMock.mock.calls.find(([url]) => String(url).endsWith('/games'))!;
    expect((gamesCall[1] as RequestInit).headers).toMatchObject({
      Authorization: `Bearer ${fresh}`,
    });
  });
});

describe('admin user lifecycle endpoints', () => {
  /** A token far from expiry, so no refresh machinery interferes with the assertions. */
  function withLiveToken() {
    tokens.access = makeJwt({ exp: expSecondsFromNow(3600) });
    tokens.refresh = 'refresh-1';
  }

  function lastCall(): [string, RequestInit] {
    const call = fetchMock.mock.calls.at(-1)!;
    return [String(call[0]), call[1] as RequestInit];
  }

  const adminId = '11111111-2222-3333-4444-555555555555';

  it('deactivates via PUT /deactivate rather than DELETE', async () => {
    withLiveToken();
    fetchMock.mockImplementation(async () => jsonResponse(200, { id: adminId, active: false }));

    const api = await loadApi();
    const result = await api.deactivateAdmin(adminId);

    const [url, init] = lastCall();
    expect(url).toMatch(new RegExp(`/admins/${adminId}/deactivate$`));
    expect(init.method).toBe('PUT');
    expect(result.data).toMatchObject({ active: false });
  });

  it('reactivates via PUT /activate', async () => {
    withLiveToken();
    fetchMock.mockImplementation(async () => jsonResponse(200, { id: adminId, active: true }));

    const api = await loadApi();
    const result = await api.activateAdmin(adminId);

    const [url, init] = lastCall();
    expect(url).toMatch(new RegExp(`/admins/${adminId}/activate$`));
    expect(init.method).toBe('PUT');
    expect(result.data).toMatchObject({ active: true });
  });

  it('reads the delete precondition from GET /references', async () => {
    withLiveToken();
    const references = {
      moderatedPictures: 0,
      sentMessages: 3,
      verifiedAchievements: 0,
      pointEntries: 0,
      automationRules: 0,
    };
    fetchMock.mockImplementation(async () => jsonResponse(200, references));

    const api = await loadApi();
    const result = await api.getAdminReferences(adminId);

    const [url, init] = lastCall();
    expect(url).toMatch(new RegExp(`/admins/${adminId}/references$`));
    expect(init.method ?? 'GET').toBe('GET');
    expect(result.data).toEqual(references);
  });

  it('deletes permanently via DELETE and reports 204', async () => {
    withLiveToken();
    fetchMock.mockImplementation(async () => jsonResponse(204, null));

    const api = await loadApi();
    const result = await api.deleteAdmin(adminId);

    const [url, init] = lastCall();
    expect(url).toMatch(new RegExp(`/admins/${adminId}$`));
    expect(init.method).toBe('DELETE');
    expect(result.status).toBe(204);
  });

  it('surfaces the 409 a blocked delete returns', async () => {
    withLiveToken();
    fetchMock.mockImplementation(async () =>
      jsonResponse(409, { message: 'Cannot delete an admin user with attributed content: 3 sent messages' }),
    );

    const api = await loadApi();
    const result = await api.deleteAdmin(adminId);

    expect(result.status).toBe(409);
    expect(result.data).toBeUndefined();
    expect(result.error).toContain('3 sent messages');
  });
});
