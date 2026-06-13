/**
 * Mock session seeding
 *
 * Mock mode (VITE_USE_MOCKS=true) has no backend and no login flow, but the
 * route guards in App.tsx still require a session: AdminRoute needs a token
 * with an admin role, and TeamRoute needs a check-in (teamId + gameId).
 *
 * These helpers write a fake-but-decodable session into localStorage so the
 * launcher can drop the developer straight into either interface. Nothing
 * verifies the token signature client-side, so a dummy signature segment is
 * fine. The pages themselves render with placeholder/empty state because the
 * API calls have no backend to answer them.
 */

import { storage } from './storage';

/** Mock IDs documented in CLAUDE.md ("Key Mock Data IDs"). */
const MOCK_TEAM_ID = '11111111-1111-1111-1111-111111111111'; // De Watervlooien
const MOCK_GAME_ID = 'a1b2c3d4-e5f6-7890-abcd-ef1234567890';

interface FakeJwtPayload {
  sub: string;
  name?: string;
  groups: string[];
}

/** URL-safe base64 encoding for a JWT segment. */
function base64UrlEncode(value: string): string {
  return btoa(value).replace(/\+/g, '-').replace(/\//g, '_').replace(/=+$/, '');
}

/**
 * Build a structurally-valid JWT (`header.payload.signature`) that
 * parseJwtPayload() can decode. The exp is set far in the future so the
 * proactive refresh logic never tries to renew it.
 */
function buildFakeJwt(payload: FakeJwtPayload): string {
  const header = { alg: 'none', typ: 'JWT' };
  const farFutureExp = Math.floor(Date.now() / 1000) + 365 * 24 * 60 * 60; // +1 year
  const body = { ...payload, exp: farFutureExp, iat: Math.floor(Date.now() / 1000) };
  return [
    base64UrlEncode(JSON.stringify(header)),
    base64UrlEncode(JSON.stringify(body)),
    'mock-signature',
  ].join('.');
}

/** Seed an admin (super_admin) session so AdminRoute passes and all menus show. */
export function seedMockAdminSession(): void {
  const token = buildFakeJwt({
    sub: 'mock-admin',
    name: 'Mock Admin',
    groups: ['admin', 'super_admin'],
  });
  storage.setAuthToken(token);
  storage.setUserRole('admin');
  storage.setUserRoles(['admin', 'super_admin']);
}

/** Seed a checked-in team session so TeamRoute passes. */
export function seedMockTeamSession(): void {
  const token = buildFakeJwt({
    sub: MOCK_TEAM_ID,
    name: 'De Watervlooien',
    groups: ['team'],
  });
  storage.setAuthToken(token);
  storage.setTeamId(MOCK_TEAM_ID);
  storage.setGameId(MOCK_GAME_ID);
  storage.setUserRole('team');
  storage.setUserRoles(['team']);
}
