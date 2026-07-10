import { afterEach, describe, expect, it, vi } from 'vitest';
import {
  parseJwtPayload,
  isTokenExpired,
  isTokenExpiringSoon,
  getUserRoleFromRoles,
  getRolesFromToken,
  getSubjectFromToken,
  getNameFromToken,
  isAdmin,
  canAccessAdminPath,
} from './auth';
import { makeJwt, expSecondsFromNow } from '../test/jwt';

const FIXED_NOW = new Date('2026-06-24T12:00:00.000Z');

describe('parseJwtPayload', () => {
  it('decodes a valid token payload', () => {
    const token = makeJwt({ sub: 'team-1', groups: ['team'], exp: 1234567890 });
    expect(parseJwtPayload(token)).toMatchObject({
      sub: 'team-1',
      groups: ['team'],
      exp: 1234567890,
    });
  });

  it('returns null when the token does not have three segments', () => {
    expect(parseJwtPayload('only.two')).toBeNull();
    expect(parseJwtPayload('a.b.c.d')).toBeNull();
    expect(parseJwtPayload('')).toBeNull();
  });

  it('returns null for an unparseable payload segment', () => {
    expect(parseJwtPayload('header.!!!not-base64!!!.sig')).toBeNull();
  });

  it('decodes URL-safe base64 payloads (_ character)', () => {
    // 0xFF bytes encode to '/' in standard base64, which makeJwt rewrites to the
    // url-safe '_'. This guarantees parseJwtPayload's '_' -> '/' reversal is exercised.
    const claims = { sub: 'ÿÿÿ', name: 'team' };
    const token = makeJwt(claims);
    const middle = token.split('.')[1];
    expect(middle).toContain('_'); // ensure the url-safe path is actually tested
    expect(parseJwtPayload(token)).toMatchObject(claims);
  });
});

describe('token expiry', () => {
  afterEach(() => {
    vi.useRealTimers();
  });

  function freezeClock() {
    vi.useFakeTimers();
    vi.setSystemTime(FIXED_NOW);
  }

  it('treats a token without an exp claim as expired', () => {
    freezeClock();
    const token = makeJwt({ sub: 'x' }); // no exp
    expect(isTokenExpired(token)).toBe(true);
    expect(isTokenExpiringSoon(token)).toBe(true);
  });

  it('treats a malformed token as expired', () => {
    freezeClock();
    expect(isTokenExpired('garbage')).toBe(true);
    expect(isTokenExpiringSoon('garbage')).toBe(true);
  });

  it('isTokenExpired: past is expired, future is not, exactly-now is expired', () => {
    freezeClock();
    expect(isTokenExpired(makeJwt({ exp: expSecondsFromNow(-1) }))).toBe(true);
    expect(isTokenExpired(makeJwt({ exp: expSecondsFromNow(60) }))).toBe(false);
    // Date.now() >= exp*1000 -> exactly at expiry counts as expired
    expect(isTokenExpired(makeJwt({ exp: Math.floor(FIXED_NOW.getTime() / 1000) }))).toBe(true);
  });

  it('isTokenExpiringSoon: within the default 5-minute window', () => {
    freezeClock();
    // expires in 4m59s -> soon
    expect(isTokenExpiringSoon(makeJwt({ exp: expSecondsFromNow(4 * 60 + 59) }))).toBe(true);
    // expires in 5m01s -> not soon
    expect(isTokenExpiringSoon(makeJwt({ exp: expSecondsFromNow(5 * 60 + 1) }))).toBe(false);
  });

  it('isTokenExpiringSoon: honors a custom threshold', () => {
    freezeClock();
    const token = makeJwt({ exp: expSecondsFromNow(8 * 60) }); // 8 minutes out
    expect(isTokenExpiringSoon(token, 10)).toBe(true); // within 10m
    expect(isTokenExpiringSoon(token, 5)).toBe(false); // outside 5m
  });
});

describe('role helpers', () => {
  it('getUserRoleFromRoles prefers admin over team', () => {
    expect(getUserRoleFromRoles(['team', 'super_admin'])).toBe('admin');
    expect(getUserRoleFromRoles(['team'])).toBe('team');
    expect(getUserRoleFromRoles([])).toBeNull();
    expect(getUserRoleFromRoles(['unknown'])).toBeNull();
  });

  it.each(['admin', 'super_admin', 'viewer', 'game_master', 'message_sender', 'checkpoint_manager', 'ranking'])(
    'treats %s as an admin role',
    (role) => {
      expect(getUserRoleFromRoles([role])).toBe('admin');
    }
  );

  it('getRolesFromToken returns the groups claim or an empty array', () => {
    expect(getRolesFromToken(makeJwt({ groups: ['team', 'viewer'] }))).toEqual(['team', 'viewer']);
    expect(getRolesFromToken(makeJwt({ sub: 'x' }))).toEqual([]);
    expect(getRolesFromToken('garbage')).toEqual([]);
  });

  it('getSubjectFromToken / getNameFromToken read their claims', () => {
    const token = makeJwt({ sub: 'team-42', name: 'De Watervlooien' });
    expect(getSubjectFromToken(token)).toBe('team-42');
    expect(getNameFromToken(token)).toBe('De Watervlooien');
    expect(getSubjectFromToken('garbage')).toBeNull();
    expect(getNameFromToken(makeJwt({ sub: 'x' }))).toBeNull();
  });

  it('isAdmin reflects any admin role in groups', () => {
    expect(isAdmin(makeJwt({ groups: ['checkpoint_manager'] }))).toBe(true);
    expect(isAdmin(makeJwt({ groups: ['team'] }))).toBe(false);
    expect(isAdmin(makeJwt({ sub: 'x' }))).toBe(false);
  });
});

describe('canAccessAdminPath — ranking page', () => {
  it('grants access to holders of the ranking role', () => {
    expect(canAccessAdminPath(makeJwt({ groups: ['ranking'] }), '/admin/ranking')).toBe(true);
  });

  it('grants access to a full admin (implicit capability role)', () => {
    expect(canAccessAdminPath(makeJwt({ groups: ['admin'] }), '/admin/ranking')).toBe(true);
  });

  it('denies staff who lack the ranking role', () => {
    expect(canAccessAdminPath(makeJwt({ groups: ['game_master'] }), '/admin/ranking')).toBe(false);
    expect(canAccessAdminPath(makeJwt({ groups: ['viewer'] }), '/admin/ranking')).toBe(false);
  });

  it('does not let the ranking role reach unrelated gated pages', () => {
    expect(canAccessAdminPath(makeJwt({ groups: ['ranking'] }), '/admin/admin-users')).toBe(false);
    expect(canAccessAdminPath(makeJwt({ groups: ['ranking'] }), '/admin/games')).toBe(false);
  });
});
