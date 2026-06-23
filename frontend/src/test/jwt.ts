/**
 * Test helper: build unsigned JWTs with arbitrary claims.
 *
 * The frontend only ever decodes the payload segment (see parseJwtPayload in
 * services/auth.ts), so a real signature is unnecessary — we just need three
 * dot-separated, base64url-encoded segments.
 */

interface JwtClaims {
  sub?: string;
  name?: string;
  groups?: string[];
  exp?: number;
  iat?: number;
  [key: string]: unknown;
}

function base64url(obj: unknown): string {
  return btoa(JSON.stringify(obj))
    .replace(/\+/g, '-')
    .replace(/\//g, '_')
    .replace(/=+$/, '');
}

/** Build an unsigned JWT (`header.payload.sig`) with the given claims. */
export function makeJwt(claims: JwtClaims): string {
  const header = base64url({ alg: 'none', typ: 'JWT' });
  const payload = base64url(claims);
  return `${header}.${payload}.sig`;
}

/** Seconds-since-epoch `exp` claim `secondsFromNow` away from the current clock. */
export function expSecondsFromNow(secondsFromNow: number): number {
  return Math.floor(Date.now() / 1000) + secondsFromNow;
}
